package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayContextBudgetAllocatorTests {
    private final ReplayContextBudgetAllocator allocator = new ReplayContextBudgetAllocator();

    @Test
    void greedilySelectsByRankAndContinuesAfterItemsThatDoNotFit() {
        ReplayContextBudget budget = new ReplayContextBudget(140, 40);
        ReplayContextBudgetItem a = item(1, 50);
        ReplayContextBudgetItem b = item(2, 60);
        ReplayContextBudgetItem c = item(3, 30);
        ReplayContextBudgetItem d = item(4, 20);

        ReplayContextBudgetAllocation allocation = allocator.allocate(budget, List.of(d, c, b, a));

        assertThat(budget.availableTokens()).isEqualTo(100);
        assertThat(allocation.selectedItems()).containsExactly(a, c, d);
        assertThat(allocation.skippedItems()).containsExactly(b);
        assertThat(allocation.usedTokens()).isEqualTo(100);
        assertThat(allocation.remainingTokens()).isZero();
        assertThat(allocation.usedTokens()).isLessThanOrEqualTo(budget.availableTokens());
    }

    @Test
    void skipsItemLargerThanEntireBudgetAndKeepsFullRemainder() {
        ReplayContextBudget budget = new ReplayContextBudget(100, 20);
        ReplayContextBudgetItem oversized = item(1, 100);

        ReplayContextBudgetAllocation allocation = allocator.allocate(budget, List.of(oversized));

        assertThat(allocation.selectedItems()).isEmpty();
        assertThat(allocation.skippedItems()).containsExactly(oversized);
        assertThat(allocation.usedTokens()).isZero();
        assertThat(allocation.remainingTokens()).isEqualTo(80);
    }

    @Test
    void rejectsDuplicateReplayIdsAndRanks() {
        UUID replayId = UUID.randomUUID();
        assertThatThrownBy(() -> allocator.allocate(new ReplayContextBudget(100, 0),
                List.of(new ReplayContextBudgetItem(replayId, 1, 10), new ReplayContextBudgetItem(replayId, 2, 10))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("replayId duplicado");
        assertThatThrownBy(() -> allocator.allocate(new ReplayContextBudget(100, 0),
                List.of(new ReplayContextBudgetItem(UUID.randomUUID(), 1, 10), new ReplayContextBudgetItem(UUID.randomUUID(), 1, 10))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retrievalRank duplicado");
    }

    @Test
    void validatesBudgetAndItemBoundaries() {
        assertThatThrownBy(() -> new ReplayContextBudget(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudget(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudget(10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudget(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudget(10, 11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudgetItem(null, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudgetItem(UUID.randomUUID(), 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudgetItem(UUID.randomUUID(), 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextBudgetItem(UUID.randomUUID(), 1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyInputProducesImmutableEmptyAllocation() {
        ReplayContextBudgetAllocation allocation = allocator.allocate(new ReplayContextBudget(50, 10), List.of());
        assertThat(allocation.selectedItems()).isEmpty();
        assertThat(allocation.skippedItems()).isEmpty();
        assertThat(allocation.usedTokens()).isZero();
        assertThat(allocation.remainingTokens()).isEqualTo(40);
        assertThatThrownBy(() -> allocation.selectedItems().add(item(1, 1))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> allocation.skippedItems().add(item(1, 1))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allocationIsDeterministicForSameOutOfOrderInput() {
        List<ReplayContextBudgetItem> items = List.of(item(3, 45), item(1, 40), item(4, 10), item(2, 35));
        ReplayContextBudget budget = new ReplayContextBudget(100, 20);

        ReplayContextBudgetAllocation first = allocator.allocate(budget, items);
        ReplayContextBudgetAllocation second = allocator.allocate(budget, items);

        assertThat(first).isEqualTo(second);
        assertThat(first.selectedItems()).extracting(ReplayContextBudgetItem::retrievalRank).containsExactly(1, 2);
        assertThat(first.skippedItems()).extracting(ReplayContextBudgetItem::retrievalRank).containsExactly(3, 4);
    }

    private ReplayContextBudgetItem item(int rank, int tokens) {
        return new ReplayContextBudgetItem(UUID.nameUUIDFromBytes(("replay-" + rank).getBytes()), rank, tokens);
    }
}
