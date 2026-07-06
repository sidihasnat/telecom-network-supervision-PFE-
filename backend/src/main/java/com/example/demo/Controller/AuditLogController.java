package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.model.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;


    @GetMapping
    public List<AuditLog> getAuditLog(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        return auditLogService.getFiltered(type, period);
    }
}
