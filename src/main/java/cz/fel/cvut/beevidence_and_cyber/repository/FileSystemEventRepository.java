package cz.fel.cvut.beevidence_and_cyber.repository;

import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.dao.FileSystemEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileSystemEventRepository extends JpaRepository<FileSystemEvent, UUID> {
    List<FileSystemEvent> findByDeviceOrderByOccurredAtDesc(EndpointDevice device);
}
