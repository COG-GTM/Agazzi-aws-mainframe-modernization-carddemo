package com.carddemo.batch;

import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

final class ReportSupport {

    private ReportSupport() {
    }

    static FlatFileItemWriter<String> writer(Path output) {
        return new FlatFileItemWriterBuilder<String>()
                .name("reportWriter-" + output.getFileName())
                .resource(new FileSystemResource(output))
                .lineAggregator(item -> item)
                .shouldDeleteIfExists(true)
                .build();
    }

    static List<String> banner(String program, String... lines) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                "START OF EXECUTION OF PROGRAM " + program),
                        java.util.stream.Stream.concat(
                                java.util.Arrays.stream(lines),
                                java.util.stream.Stream.of(
                                        "END OF EXECUTION OF PROGRAM " + program)))
                .toList();
    }

    static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2).toPlainString();
    }
}
