package com.carddemo.service;

import com.carddemo.dto.request.CardUpdateRequest;
import com.carddemo.dto.response.CardResponse;
import com.carddemo.entity.Card;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.AccountCardXrefRepository;
import com.carddemo.repository.CardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migrated from COBOL programs COCRDLIC.cbl (Card List), COCRDSLC.cbl (Card View),
 * and COCRDUPC.cbl (Card Update).
 * Original: CICS transactions CCLI/CCDL/CCUP with VSAM KSDS CARDFILE.
 */
@Service
public class CardService {

    private final CardRepository cardRepository;
    private final AccountCardXrefRepository xrefRepository;

    public CardService(CardRepository cardRepository, AccountCardXrefRepository xrefRepository) {
        this.cardRepository = cardRepository;
        this.xrefRepository = xrefRepository;
    }

    public List<CardResponse> getCardsByAccount(Long acctId) {
        return cardRepository.findByAcctId(acctId).stream()
                .map(CardResponse::from)
                .toList();
    }

    public CardResponse getCard(String cardNum) {
        Card card = cardRepository.findById(cardNum)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardNum));
        return CardResponse.from(card);
    }

    @Transactional
    public CardResponse updateCard(String cardNum, CardUpdateRequest request) {
        Card card = cardRepository.findById(cardNum)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found: " + cardNum));

        if (request.embossedName() != null) {
            if (request.embossedName().isBlank()) {
                throw new BusinessRuleException("Embossed name cannot be blank");
            }
            card.setEmbossedName(request.embossedName());
        }

        if (request.activeStatus() != null) {
            if (!"Y".equals(request.activeStatus()) && !"N".equals(request.activeStatus())) {
                throw new BusinessRuleException("Active status must be 'Y' or 'N'");
            }
            card.setActiveStatus(request.activeStatus());
        }

        cardRepository.save(card);
        return CardResponse.from(card);
    }

    public List<CardResponse> getCardsByCustomer(Long custId) {
        return xrefRepository.findByCustId(custId).stream()
                .map(xref -> cardRepository.findById(xref.getCardNum())
                        .map(CardResponse::from)
                        .orElse(null))
                .filter(c -> c != null)
                .toList();
    }
}
