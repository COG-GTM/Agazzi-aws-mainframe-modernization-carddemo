package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.ArrayList;
import java.util.List;

final class StatementTasklet implements Tasklet {

    private final StatementDataService data;
    private final FlatFileItemWriter<String> textWriter;
    private final FlatFileItemWriter<String> htmlWriter;
    private final StatementRenderer renderer;

    StatementTasklet(
            StatementDataService data,
            FlatFileItemWriter<String> textWriter,
            FlatFileItemWriter<String> htmlWriter) {
        this.data = data;
        this.textWriter = textWriter;
        this.htmlWriter = htmlWriter;
        this.renderer = new StatementRenderer();
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context)
            throws Exception {
        var executionContext = context.getStepContext()
                .getStepExecution()
                .getExecutionContext();
        textWriter.open(executionContext);
        htmlWriter.open(executionContext);
        List<String> text = new ArrayList<>();
        List<String> html = new ArrayList<>();
        html.add("<!DOCTYPE html>");
        html.add("<html lang=\"en\">");
        html.add("<head>");
        html.add("<meta charset=\"utf-8\">");
        html.add("<title>HTML Table Layout</title>");
        html.add("</head>");
        html.add("<body style=\"margin:0px;\">");
        html.add("<table  align=\"center\" frame=\"box\" "
                + "style=\"width:70%; font:12px Segoe UI,sans-serif;\">");
        data.open();
        try {
            for (CardXref xref : data.readXrefs()) {
                Account account = data.readAccount(xref.getAccountId()).orElse(null);
                Customer customer = data.readCustomer(xref.getCustomerId()).orElse(null);
                if (account == null || customer == null) {
                    continue;
                }
                List<Transaction> transactions = data.readTransactions(xref.getCardNumber());
                text.addAll(renderer.text(account, customer, transactions));
                html.addAll(renderer.html(account, customer, transactions));
            }
        } finally {
            data.close();
        }
        html.add("</table>");
        html.add("</body>");
        html.add("</html>");
        textWriter.write(new Chunk<>(text));
        htmlWriter.write(new Chunk<>(html));
        textWriter.close();
        htmlWriter.close();
        contribution.incrementWriteCount(text.size() + html.size());
        return RepeatStatus.FINISHED;
    }
}
