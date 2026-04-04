package cz.fel.cvut.beevidence_and_cyber.service;

import cz.fel.cvut.beevidence_and_cyber.config.LdapProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.User;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceSubnetScanRequest;
import cz.fel.cvut.beevidence_and_cyber.dto.DeviceSubnetScanResultDto;
import cz.fel.cvut.beevidence_and_cyber.dto.DiscoveredDeviceDto;
import cz.fel.cvut.beevidence_and_cyber.enumeration.ActorSourceEnum;
import cz.fel.cvut.beevidence_and_cyber.enumeration.AuditResultEnum;
import cz.fel.cvut.beevidence_and_cyber.exception.BadRequestException;
import cz.fel.cvut.beevidence_and_cyber.repository.EndpointDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubnetScanService {

    private static final int DEFAULT_TIMEOUT_MS = 1000;
    private static final int DEFAULT_MAX_HOSTS = 256;
    private static final List<Integer> FALLBACK_TCP_PORTS = List.of(135, 139, 445, 3389, 80, 443);

    private final EndpointDeviceRepository endpointDeviceRepository;
    private final AuditService auditService;
    private final LdapProperties ldapProperties;

    public DeviceSubnetScanResultDto scan(DeviceSubnetScanRequest request, User actorUser) {
        ParsedSubnet parsedSubnet = parseSubnet(request.subnetCidr());
        int effectiveMaxHosts = request.maxHosts() == null ? DEFAULT_MAX_HOSTS : request.maxHosts();
        int totalHosts = parsedSubnet.hostAddresses().size();
        if (totalHosts > effectiveMaxHosts) {
            throw new BadRequestException(
                    "Zadaná podsíť obsahuje " + totalHosts + " hostů, což překračuje povolený limit " + effectiveMaxHosts + "."
            );
        }

        int timeoutMs = request.timeoutMs() == null ? DEFAULT_TIMEOUT_MS : request.timeoutMs();
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Starting subnet scan. subnetCidr={}, totalHosts={}, timeoutMs={}, requestedBy={}",
                request.subnetCidr(),
                totalHosts,
                timeoutMs,
                actorUser == null ? "unknown" : actorUser.getAdUsername());
        List<DiscoveredDeviceDto> discoveredDevices = scanHosts(parsedSubnet.hostAddresses(), timeoutMs);
        LocalDateTime finishedAt = LocalDateTime.now();
        log.info("Finished subnet scan. subnetCidr={}, scannedHosts={}, activeHosts={}, durationMs={}",
                request.subnetCidr(),
                totalHosts,
                discoveredDevices.size(),
                Duration.between(startedAt, finishedAt).toMillis());

        auditService.log(
                actorUser,
                ActorSourceEnum.WEB,
                "SUBNET_SCAN",
                "SUBNET",
                null,
                AuditResultEnum.SUCCESS,
                java.util.Map.of(
                        "subnetCidr", request.subnetCidr(),
                        "activeHosts", discoveredDevices.size(),
                        "scannedHosts", totalHosts
                )
        );

        return new DeviceSubnetScanResultDto(
                request.subnetCidr(),
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt).toMillis(),
                totalHosts,
                discoveredDevices.size(),
                discoveredDevices
        );
    }

    private List<DiscoveredDeviceDto> scanHosts(List<String> hostAddresses, int timeoutMs) {
        int poolSize = Math.max(4, Math.min(32, hostAddresses.size()));
        try (ExecutorService executorService = Executors.newFixedThreadPool(poolSize)) {
            List<Callable<DiscoveredDeviceDto>> tasks = hostAddresses.stream()
                    .<Callable<DiscoveredDeviceDto>>map(address -> () -> probeHost(address, timeoutMs))
                    .toList();

            List<Future<DiscoveredDeviceDto>> futures = executorService.invokeAll(tasks);
            List<DiscoveredDeviceDto> activeHosts = new ArrayList<>();
            for (Future<DiscoveredDeviceDto> future : futures) {
                try {
                    DiscoveredDeviceDto discoveredDevice = future.get();
                    if (discoveredDevice != null) {
                        activeHosts.add(discoveredDevice);
                    }
                } catch (Exception ignored) {
                }
            }
            return activeHosts.stream()
                    .sorted(Comparator.comparing(DiscoveredDeviceDto::ipAddress, this::compareIpAddresses))
                    .toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Sken podsítě byl přerušen.");
        }
    }

    private DiscoveredDeviceDto probeHost(String ipAddress, int timeoutMs) {
        long startedAt = System.nanoTime();
        try {
            log.info("Scanning host {}", ipAddress);
            InetAddress address = InetAddress.getByName(ipAddress);
            boolean reachableByPing = canPingWithSystemCommand(ipAddress, timeoutMs);
            boolean reachableByJava = !reachableByPing && address.isReachable(timeoutMs);
            boolean reachableByTcp = !reachableByPing && !reachableByJava && canConnectToKnownTcpPort(ipAddress, Math.min(timeoutMs, 400));
            boolean reachable = reachableByPing || reachableByJava || reachableByTcp;
            if (!reachable) {
                log.info("Host {} is unreachable.", ipAddress);
                return null;
            }

            long responseTimeMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            String directHostname = safeHostname(address);
            String canonicalName = safeCanonicalHostname(address);
            String dnsNameFromDomainController = reverseLookupAgainstConfiguredDns(ipAddress);
            String fqdn = firstNonBlank(
                    fullyQualifiedName(canonicalName),
                    fullyQualifiedName(dnsNameFromDomainController)
            );
            String hostname = firstNonBlank(
                    directHostname,
                    extractHostname(dnsNameFromDomainController),
                    extractHostname(canonicalName),
                    fqdn
            );
            Optional<EndpointDevice> matchingDevice = findMatchingDevice(ipAddress, hostname, fqdn);

            DiscoveredDeviceDto result = new DiscoveredDeviceDto(
                    ipAddress,
                    hostname,
                    fqdn,
                    responseTimeMs,
                    matchingDevice.isPresent(),
                    matchingDevice.map(EndpointDevice::getId).orElse(null),
                    matchingDevice.map(EndpointDevice::getHostname).orElse(null),
                    matchingDevice.map(EndpointDevice::isAgentInstalled).orElse(false),
                    buildSuggestedHostname(ipAddress, hostname)
            );
            log.info("Host {} is active. hostname={}, fqdn={}, responseTimeMs={}, alreadyInInventory={}",
                    ipAddress,
                    result.hostname(),
                    result.fqdn(),
                    result.responseTimeMs(),
                    result.alreadyInInventory());
            if (reachableByPing) {
                log.info("Host {} was detected by system ping.", ipAddress);
            } else if (reachableByJava) {
                log.info("Host {} was detected by Java reachability.", ipAddress);
            } else if (reachableByTcp) {
                log.info("Host {} was detected by TCP port probe.", ipAddress);
            }
            return result;
        } catch (Exception ignored) {
            log.warn("Host scan failed for {}", ipAddress);
            return null;
        }
    }

    private boolean canPingWithSystemCommand(String ipAddress, int timeoutMs) {
        List<String> command = buildPingCommand(ipAddress, timeoutMs);

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            long waitTimeoutMs = Math.max((long) timeoutMs + 1500L, 2500L);
            boolean finished = process.waitFor(waitTimeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception exception) {
            log.debug("System ping failed for {}: {}", ipAddress, exception.getMessage());
            return false;
        }
    }

    private List<String> buildPingCommand(String ipAddress, int timeoutMs) {
        if (isWindows()) {
            return List.of("ping", "-n", "1", "-w", String.valueOf(timeoutMs), ipAddress);
        }
        if (isMac()) {
            return List.of("ping", "-c", "1", "-W", String.valueOf(timeoutMs), ipAddress);
        }
        int timeoutSeconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
        return List.of("ping", "-c", "1", "-W", String.valueOf(timeoutSeconds), ipAddress);
    }

    private boolean canConnectToKnownTcpPort(String ipAddress, int timeoutMs) {
        for (Integer port : FALLBACK_TCP_PORTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ipAddress, port), timeoutMs);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private Optional<EndpointDevice> findMatchingDevice(String ipAddress, String hostname, String fqdn) {
        Optional<EndpointDevice> byIp = endpointDeviceRepository.findByPrimaryIpIgnoreCase(ipAddress);
        if (byIp.isPresent()) {
            return byIp;
        }
        if (fqdn != null && !fqdn.isBlank()) {
            Optional<EndpointDevice> byFqdn = endpointDeviceRepository.findByFqdnIgnoreCase(fqdn);
            if (byFqdn.isPresent()) {
                return byFqdn;
            }
        }
        if (hostname != null && !hostname.isBlank()) {
            return endpointDeviceRepository.findByHostnameIgnoreCase(hostname);
        }
        return Optional.empty();
    }

    private String safeCanonicalHostname(InetAddress address) {
        try {
            String canonicalHostName = address.getCanonicalHostName();
            if (canonicalHostName == null || canonicalHostName.isBlank() || canonicalHostName.equals(address.getHostAddress())) {
                return null;
            }
            return canonicalHostName.trim();
        } catch (Exception exception) {
            return null;
        }
    }

    private String safeHostname(InetAddress address) {
        try {
            String hostName = address.getHostName();
            if (hostName == null || hostName.isBlank() || hostName.equals(address.getHostAddress())) {
                return null;
            }
            return extractHostname(hostName.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private String reverseLookupAgainstConfiguredDns(String ipAddress) {
        String dnsServerHost = resolveDnsServerHost();
        if (dnsServerHost == null || dnsServerHost.isBlank()) {
            return null;
        }

        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put(Context.PROVIDER_URL, "dns://" + dnsServerHost + "/");

        String reverseLookupName = buildReverseLookupName(ipAddress);
        DirContext directoryContext = null;
        try {
            directoryContext = new InitialDirContext(environment);
            Attributes attributes = directoryContext.getAttributes(reverseLookupName, new String[]{"PTR"});
            Attribute ptrAttribute = attributes.get("PTR");
            if (ptrAttribute == null) {
                return null;
            }
            NamingEnumeration<?> values = ptrAttribute.getAll();
            if (!values.hasMore()) {
                return null;
            }
            Object value = values.next();
            if (value == null) {
                return null;
            }
            String resolvedName = value.toString().trim();
            if (resolvedName.endsWith(".")) {
                resolvedName = resolvedName.substring(0, resolvedName.length() - 1);
            }
            return resolvedName.isBlank() ? null : resolvedName;
        } catch (Exception exception) {
            log.debug("Reverse DNS lookup against configured DNS server failed for {}: {}", ipAddress, exception.getMessage());
            return null;
        } finally {
            if (directoryContext != null) {
                try {
                    directoryContext.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String resolveDnsServerHost() {
        String ldapUrl = ldapProperties.getUrl();
        if (ldapUrl == null || ldapUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(ldapUrl.trim());
            return uri.getHost();
        } catch (Exception exception) {
            log.debug("Unable to parse LDAP URL '{}' for DNS reverse lookup.", ldapUrl);
            return null;
        }
    }

    private String buildReverseLookupName(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            return ipAddress;
        }
        return octets[3] + "." + octets[2] + "." + octets[1] + "." + octets[0] + ".in-addr.arpa";
    }

    private String extractHostname(String canonicalName) {
        if (canonicalName == null || canonicalName.isBlank()) {
            return null;
        }
        int dotIndex = canonicalName.indexOf('.');
        return dotIndex > 0 ? canonicalName.substring(0, dotIndex) : canonicalName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String fullyQualifiedName(String value) {
        if (value == null || value.isBlank() || !value.contains(".")) {
            return null;
        }
        return value;
    }

    private String buildSuggestedHostname(String ipAddress, String hostname) {
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        return "host-" + ipAddress.replace('.', '-');
    }

    private int compareIpAddresses(String left, String right) {
        long leftValue = ipv4ToLong(left);
        long rightValue = ipv4ToLong(right);
        return Long.compare(leftValue, rightValue);
    }

    private long ipv4ToLong(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        long value = 0;
        for (String octet : octets) {
            value = (value << 8) + Integer.parseInt(octet);
        }
        return value;
    }

    private ParsedSubnet parseSubnet(String subnetCidr) {
        try {
            String[] parts = subnetCidr.trim().split("/", 2);
            InetAddress networkAddress = InetAddress.getByName(parts[0]);
            if (!(networkAddress.getAddress().length == 4)) {
                throw new BadRequestException("Sken podsítě aktuálně podporuje pouze IPv4 rozsahy.");
            }
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                throw new BadRequestException("Maska podsítě musí být v rozsahu 0 až 32.");
            }

            long baseAddress = ipv4ToLong(parts[0]);
            long mask = prefix == 0 ? 0 : ~((1L << (32 - prefix)) - 1) & 0xFFFFFFFFL;
            long network = baseAddress & mask;
            long broadcast = network | (~mask & 0xFFFFFFFFL);

            List<String> hostAddresses = new ArrayList<>();
            long start = prefix >= 31 ? network : network + 1;
            long end = prefix >= 31 ? broadcast : broadcast - 1;
            for (long current = start; current <= end; current++) {
                hostAddresses.add(longToIpv4(current));
            }
            if (hostAddresses.isEmpty()) {
                hostAddresses.add(longToIpv4(network));
            }
            return new ParsedSubnet(hostAddresses);
        } catch (UnknownHostException | NumberFormatException exception) {
            throw new BadRequestException("Zadej platný IPv4 subnet ve formátu CIDR, například 192.168.68.0/24.");
        }
    }

    private String longToIpv4(long value) {
        return ((value >> 24) & 0xFF) + "." +
                ((value >> 16) & 0xFF) + "." +
                ((value >> 8) & 0xFF) + "." +
                (value & 0xFF);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private record ParsedSubnet(List<String> hostAddresses) {
    }

}
