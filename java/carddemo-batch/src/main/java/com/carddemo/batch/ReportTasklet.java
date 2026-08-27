package com.carddemo.batch;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

final class ReportTasklet<T> implements Tasklet {

    private final FlatFileItemWriter<String> writer;
    private final String program;
    private final Supplier<List<T>> records;
    private final Function<T, List<String>> renderer;

    ReportTasklet(
            FlatFileItemWriter<String> writer,
            String program,
            Supplier<List<T>> records,
            Function<T, List<String>> renderer) {
        this.writer = writer;
        this.program = program;
        this.records = records;
        this.renderer = renderer;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        writer.open(chunkContext.getStepContext().getStepExecution().getExecutionContext());
        List<String> detailLines = records.get().stream()
                .flatMap(record -> renderer.apply(record).stream())
                .toList();
        List<String> lines = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                "START OF EXECUTION OF PROGRAM " + program),
                        java.util.stream.Stream.concat(
                                detailLines.stream(),
                                java.util.stream.Stream.of(
                                        "END OF EXECUTION OF PROGRAM " + program)))
                .toList();
        writer.write(new Chunk<>(lines));
        writer.close();
        contribution.incrementWriteCount(lines.size());
        return RepeatStatus.FINISHED;
    }
}
