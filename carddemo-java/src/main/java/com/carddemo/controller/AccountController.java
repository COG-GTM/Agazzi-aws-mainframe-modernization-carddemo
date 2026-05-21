package com.carddemo.controller;

import com.carddemo.dto.request.AccountUpdateRequest;
import com.carddemo.dto.response.AccountResponse;
import com.carddemo.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Replaces COBOL COACTVWC/COACTUPC / CICS CAVW/CAUP")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List all accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{acctId}")
    @Operation(summary = "View account details")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long acctId) {
        return ResponseEntity.ok(accountService.getAccount(acctId));
    }

    @PutMapping("/{acctId}")
    @Operation(summary = "Update account")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long acctId,
            @Valid @RequestBody AccountUpdateRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(acctId, request));
    }
}
