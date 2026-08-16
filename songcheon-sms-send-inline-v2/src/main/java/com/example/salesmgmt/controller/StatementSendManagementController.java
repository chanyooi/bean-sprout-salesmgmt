package com.example.salesmgmt.controller;

import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/statement-send/manage")
public class StatementSendManagementController {

    private final ApplicationContext applicationContext;

    public StatementSendManagementController(
            ApplicationContext applicationContext
    ) {
        this.applicationContext = applicationContext;
    }

    @GetMapping("/logs")
    public ResponseEntity<List<Map<String, Object>>> logs(
            @RequestParam(required = false) String month
    ) {
        Object repository = findDeliveryLogRepository();
        Iterable<?> entities = findAll(repository);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object entity : entities) {
            if (entity == null) continue;

            String entityMonth = readMonth(entity);

            if (
                    month != null
                    && !month.isBlank()
                    && !month.equals(entityMonth)
            ) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", readId(entity));
            row.put("month", entityMonth);
            row.put("vendorName", readVendorName(entity));
            row.put("sentAt", readSentAt(entity));
            result.add(row);
        }

        result.sort(
                Comparator.comparing(
                        row -> String.valueOf(
                                row.getOrDefault(
                                        "sentAt",
                                        ""
                                )
                        ),
                        Comparator.reverseOrder()
                )
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/logs/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String id
    ) {
        Object repository = findDeliveryLogRepository();
        Iterable<?> entities = findAll(repository);

        Object target = null;

        for (Object entity : entities) {
            if (
                    entity != null
                    && id.equals(
                            String.valueOf(
                                    readId(entity)
                            )
                    )
            ) {
                target = entity;
                break;
            }
        }

        if (target == null) {
            return ResponseEntity.notFound().build();
        }

        invokeDelete(repository, target);

        return ResponseEntity.ok(
                Map.of("deleted", true)
        );
    }

    private Object findDeliveryLogRepository() {
        String[] beanNames =
                applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            String normalized =
                    beanName.toLowerCase();

            if (
                    normalized.contains(
                            "statementdeliverylog"
                    )
                    && normalized.contains(
                            "repository"
                    )
            ) {
                return applicationContext.getBean(
                        beanName
                );
            }
        }

        throw new IllegalStateException(
                "StatementDeliveryLog Repository Bean을 찾지 못했습니다."
        );
    }

    private Iterable<?> findAll(
            Object repository
    ) {
        try {
            Method method =
                    repository.getClass()
                            .getMethod("findAll");

            Object value =
                    method.invoke(repository);

            if (value instanceof Iterable<?> iterable) {
                return iterable;
            }

            throw new IllegalStateException(
                    "발송기록 findAll 결과를 읽을 수 없습니다."
            );

        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "발송기록을 불러오지 못했습니다.",
                    exception
            );
        }
    }

    private void invokeDelete(
            Object repository,
            Object entity
    ) {
        for (Method method
                : repository.getClass().getMethods()) {

            if (
                    method.getName().equals("delete")
                    && method.getParameterCount() == 1
            ) {
                try {
                    method.invoke(
                            repository,
                            entity
                    );
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }

        throw new IllegalStateException(
                "발송기록 삭제 메서드를 찾지 못했습니다."
        );
    }

    private Object readId(
            Object entity
    ) {
        Object value =
                readValue(
                        entity,
                        "id",
                        "logId"
                );

        return value == null
                ? ""
                : value;
    }

    private String readMonth(
            Object entity
    ) {
        Object value =
                readValue(
                        entity,
                        "month",
                        "statementMonth",
                        "sentMonth",
                        "billingMonth"
                );

        return value == null
                ? ""
                : String.valueOf(value);
    }

    private String readSentAt(
            Object entity
    ) {
        Object value =
                readValue(
                        entity,
                        "sentAt",
                        "createdAt",
                        "sentDateTime",
                        "sentTime"
                );

        if (value == null) {
            return "";
        }

        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }

        return String.valueOf(value);
    }

    private String readVendorName(
            Object entity
    ) {
        Object directName =
                readValue(
                        entity,
                        "vendorName",
                        "statementName"
                );

        if (directName != null) {
            return String.valueOf(directName);
        }

        Object vendor =
                readValue(
                        entity,
                        "vendor"
                );

        if (vendor != null) {
            Object name =
                    readValue(
                            vendor,
                            "statementName",
                            "inputName",
                            "vendorName",
                            "name"
                    );

            if (name != null) {
                return String.valueOf(name);
            }

            Object vendorId =
                    readValue(
                            vendor,
                            "id"
                    );

            if (vendorId != null) {
                return "거래처 #" + vendorId;
            }
        }

        Object vendorId =
                readValue(
                        entity,
                        "vendorId"
                );

        return vendorId == null
                ? "거래처"
                : "거래처 #" + vendorId;
    }

    private Object readValue(
            Object target,
            String... names
    ) {
        if (target == null) {
            return null;
        }

        for (String name : names) {
            try {
                Method direct =
                        target.getClass()
                                .getMethod(name);

                return direct.invoke(target);

            } catch (ReflectiveOperationException ignored) {
            }

            String getter =
                    "get"
                            + Character.toUpperCase(
                                    name.charAt(0)
                            )
                            + name.substring(1);

            try {
                Method method =
                        target.getClass()
                                .getMethod(getter);

                return method.invoke(target);

            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }
}
