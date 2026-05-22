package com.carddemo.controller;

import com.carddemo.dto.request.BillPaymentRequest;
import com.carddemo.dto.request.TransactionAddRequest;
import com.carddemo.dto.response.TransactionResponse;
import com.carddemo.service.BillPaymentService;
import com.carddemo.service.ReportService;
import com.carddemo.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Replaces COBOL COTRN00C/01C/02C, COBIL00C, CORPT00C / CICS CT00/01/02/CB00/CR00")
public class TransactionController {

    private final TransactionService transactionService;
    private final BillPaymentService billPaymentService;
    private final ReportService reportService;

    public TransactionController(TransactionService transactionService,
                                 BillPaymentService billPaymentService,
                                 ReportService reportService) {
        this.transactionService = transactionService;
        this.billPaymentService = billPaymentService;
        this.reportService = reportService;
    }

    @GetMapping("/card/{cardNum}")
    @Operation(summary = "List transactions by card number")
    public ResponseEntity<Page<TransactionResponse>> getByCard(
            @PathVariable String cardNum, Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsByCard(cardNum, pageable));
    }

    @GetMapping("/account/{acctId}")
    @Operation(summary = "List transactions by account")
    public ResponseEntity<Page<TransactionResponse>> getByAccount(
            @PathVariable Long acctId, Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(acctId, pageable));
    }

    @GetMapping("/{tranId}")
    @Operation(summary = "View transaction details")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String tranId) {
        return ResponseEntity.ok(transactionService.getTransaction(tranId));
    }

    @PostMapping
    @Operation(summary = "Add new transaction")
    public ResponseEntity<TransactionResponse> addTransaction(
            @Valid @RequestBody TransactionAddRequest request) {
        return ResponseEntity.ok(transactionService.addTransaction(request));
    }

    @PostMapping("/bill-payment")
    @Operation(summary = "Process bill payment")
    public ResponseEntity<TransactionResponse> billPayment(
            @Valid @RequestBody BillPaymentRequest request) {
        return ResponseEntity.ok(billPaymentService.processPayment(request));
    }

    @GetMapping("/report/{acctId}")
    @Operation(summary = "Generate account statement report")
    public ResponseEntity<Map<String, Object>> getReport(
            @PathVariable Long acctId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(reportService.generateAccountStatement(acctId, startDate, endDate));
    }
}
