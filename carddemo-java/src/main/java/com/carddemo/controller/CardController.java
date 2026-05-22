package com.carddemo.controller;

import com.carddemo.dto.request.CardUpdateRequest;
import com.carddemo.dto.response.CardResponse;
import com.carddemo.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "Replaces COBOL COCRDLIC/COCRDSLC/COCRDUPC / CICS CCLI/CCDL/CCUP")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/account/{acctId}")
    @Operation(summary = "List cards by account")
    public ResponseEntity<List<CardResponse>> getCardsByAccount(@PathVariable Long acctId) {
        return ResponseEntity.ok(cardService.getCardsByAccount(acctId));
    }

    @GetMapping("/{cardNum}")
    @Operation(summary = "View card details")
    public ResponseEntity<CardResponse> getCard(@PathVariable String cardNum) {
        return ResponseEntity.ok(cardService.getCard(cardNum));
    }

    @PutMapping("/{cardNum}")
    @Operation(summary = "Update card")
    public ResponseEntity<CardResponse> updateCard(
            @PathVariable String cardNum,
            @Valid @RequestBody CardUpdateRequest request) {
        return ResponseEntity.ok(cardService.updateCard(cardNum, request));
    }

    @GetMapping("/customer/{custId}")
    @Operation(summary = "List cards by customer")
    public ResponseEntity<List<CardResponse>> getCardsByCustomer(@PathVariable Long custId) {
        return ResponseEntity.ok(cardService.getCardsByCustomer(custId));
    }
}
