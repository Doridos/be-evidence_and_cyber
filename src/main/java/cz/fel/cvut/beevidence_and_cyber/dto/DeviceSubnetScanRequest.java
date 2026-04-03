package cz.fel.cvut.beevidence_and_cyber.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceSubnetScanRequest(
        @NotBlank
        @Pattern(
                regexp = "^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)/(3[0-2]|[12]?\\d)$",
                message = "musí být platný IPv4 subnet ve formátu CIDR, například 192.168.68.0/24"
        )
        String subnetCidr,
        @Min(value = 100, message = "musí být alespoň 100 ms")
        @Max(value = 5000, message = "nesmí překročit 5000 ms")
        Integer timeoutMs,
        @Min(value = 2, message = "musí být alespoň 2 hosty")
        @Max(value = 1024, message = "nesmí překročit 1024 hostů")
        Integer maxHosts
) {
}
