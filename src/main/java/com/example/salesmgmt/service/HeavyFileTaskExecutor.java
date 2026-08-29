package com.example.salesmgmt.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Railway의 제한된 메모리에서 Apache POI 작업이 동시에 여러 개 실행되면
 * 각 XSSFWorkbook이 큰 메모리를 사용해 서버가 재시작될 수 있습니다.
 * 업로드/명세서/새 장부 생성 같은 무거운 파일 작업을 한 줄로 세워 처리합니다.
 */
@Service
public class HeavyFileTaskExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "heavy-file-worker");
        thread.setDaemon(true);
        return thread;
    });

    public void submit(Runnable task) {
        executor.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
