package com.fluxpay.audit.service;

import com.fluxpay.audit.dto.AuditLogDto;
import com.fluxpay.audit.dto.CreateAuditLogRequest;
import com.fluxpay.audit.entity.AuditLog;
import com.fluxpay.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLogDto createAuditLog(CreateAuditLogRequest request) {
        AuditLog log = AuditLog.builder()
                .userId(request.getUserId())
                .action(request.getAction())
                .resource(request.getResource())
                .resourceId(request.getResourceId())
                .metadata(request.getMetadata())
                .build();

        log = auditLogRepository.save(log);
        return mapToDto(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> getUserAuditLogs(UUID userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private AuditLogDto mapToDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .action(log.getAction())
                .resource(log.getResource())
                .resourceId(log.getResourceId())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
