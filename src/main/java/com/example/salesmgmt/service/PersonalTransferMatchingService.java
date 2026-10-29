package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.PersonalBankTransactionEntity;
import com.example.salesmgmt.repository.PersonalBankTransactionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Match transfers between the bank accounts already imported into the private ledger.
 * We do NOT know the bank account owner's name, so matching is a conservative
 * same-day/identical-amount suggestion limited to a unique one-in/one-out
 * pair across distinct registered account numbers.
 */
@Service
public class PersonalTransferMatchingService {
    private final PersonalBankTransactionRepository transactions;

    public PersonalTransferMatchingService(PersonalBankTransactionRepository transactions) {
        this.transactions=transactions;
    }

    /**
     * Also processes the existing uploaded history after a new deployment.
     * Idempotent: an already paired transaction is never counted twice.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileExistingOnStartup() {
        reconcile();
    }

    public record Summary(int newPairs, int totalPaired, int ambiguousGroups) {}

    @Transactional
    public Summary reconcile() {
        var rows=transactions.findAll();
        Map<TransferKey,List<PersonalBankTransactionEntity>> byDateAndAmount=new HashMap<>();
        for(var row:rows) {
            if(row.isTransferOptOut()) continue;
            if(!(row.isAutoTransfer() || isUnclassified(row.getCategory()))) continue;
            BigDecimal amount = row.getIncoming().signum()>0 && row.getOutgoing().signum()==0
                ? row.getIncoming()
                : row.getOutgoing().signum()>0 && row.getIncoming().signum()==0
                    ? row.getOutgoing() : BigDecimal.ZERO;
            if(amount.signum()<=0)continue;
            var key=new TransferKey(row.getTransactionDate(),amount.stripTrailingZeros());
            byDateAndAmount.computeIfAbsent(key,ignored->new ArrayList<>()).add(row);
        }

        int newPairs=0;
        int ambiguousGroups=0;
        for(var group:byDateAndAmount.values()) {
            var incoming=group.stream().filter(row->row.getIncoming().signum()>0).toList();
            var outgoing=group.stream().filter(row->row.getOutgoing().signum()>0).toList();

            // Any ambiguous candidate is left alone (no arbitrary first match).
            if(incoming.size()!=1||outgoing.size()!=1) {
                if(!incoming.isEmpty()&&!outgoing.isEmpty())ambiguousGroups++;
                continue;
            }
            var in=incoming.get(0);
            var out=outgoing.get(0);
            if(in.getAccount().equals(out.getAccount()))continue;
            if((in.isAutoTransfer()&&!Objects.equals(in.getMatchedTransferId(),out.getId()))
                    ||(out.isAutoTransfer()&&!Objects.equals(out.getMatchedTransferId(),in.getId()))) continue;
            if(!in.isAutoTransfer()&&!out.isAutoTransfer())newPairs++;
            in.markAutomaticTransfer(out.getId());
            out.markAutomaticTransfer(in.getId());
        }
        int totalPairs=(int)rows.stream().filter(PersonalBankTransactionEntity::isAutoTransfer)
                .filter(r->r.getIncoming().signum()>0).count();
        return new Summary(newPairs,totalPairs,ambiguousGroups);
    }

    /**
     * Preserve explicit human decisions: editing a category or connecting an
     * incoming transaction to a business vendor detaches a previously paired
     * transfer, restores the other side's prior category, and prevents
     * automatic recategorization of the explicitly edited entry.
     */
    @Transactional
    public void releaseForManualChoice(PersonalBankTransactionEntity row) {
        if(row.isAutoTransfer()) {
            Long counterpartId=row.getMatchedTransferId();
            row.clearAutomaticTransfer();
            if(counterpartId!=null) {
                transactions.findById(counterpartId).ifPresent(other->{
                    if(other.isAutoTransfer()
                        &&Objects.equals(other.getMatchedTransferId(),row.getId()))
                        other.clearAutomaticTransfer();
                });
            }
        }
        row.optOutAutomaticTransfer();
    }

    private static boolean isUnclassified(String category) {
        return category==null || category.isBlank() || category.startsWith("미분류");
    }

    private record TransferKey(LocalDate date,BigDecimal amount) {}
}
