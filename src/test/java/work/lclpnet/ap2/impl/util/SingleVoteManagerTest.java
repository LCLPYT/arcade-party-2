package work.lclpnet.ap2.impl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SingleVoteManagerTest {

    SingleVoteManager<String> voteManager;

    @BeforeEach
    void setUp() {
        voteManager = new SingleVoteManager<>(true);
    }

    @Test
    void vote_single_succeeds() {
        assertTrue(voteManager.vote(UUID.randomUUID(), "foo"));
    }

    @Test
    void vote_singleChanged_succeeds() {
        UUID voter = UUID.randomUUID();
        assertTrue(voteManager.vote(voter, "foo"));
        assertTrue(voteManager.vote(voter, "bar"));
    }

    @Test
    void vote_changeDisallowed_singleChangeSucceeds() {
        voteManager = new SingleVoteManager<>(false);

        UUID voter = UUID.randomUUID();
        assertTrue(voteManager.vote(voter, "foo"));
    }

    @Test
    void vote_changeDisallowed_changeFails() {
        voteManager = new SingleVoteManager<>(false);

        UUID voter = UUID.randomUUID();
        assertTrue(voteManager.vote(voter, "foo"));
        assertFalse(voteManager.vote(voter, "bar"));
    }

    @Test
    void getVote_noVote_empty() {
        assertEquals(Optional.empty(), voteManager.getVote(UUID.randomUUID()));
    }

    @Test
    void getVote_voted_vote() {
        UUID voter = UUID.randomUUID();
        voteManager.vote(voter, "foo");

        assertEquals(Optional.of("foo"), voteManager.getVote(voter));
    }

    @Test
    void getVote_voteChanged_correctVote() {
        UUID voter = UUID.randomUUID();
        voteManager.vote(voter, "foo");
        voteManager.vote(voter, "bar");

        assertEquals(Optional.of("bar"), voteManager.getVote(voter));
    }

    @Test
    void votes_initial_empty() {
        var votes = voteManager.votes();
        assertTrue(votes.isEmpty());
    }

    @Test
    void votes_single() {
        voteManager.vote(UUID.randomUUID(), "foo");
        var votes = voteManager.votes();

        assertEquals(Map.of("foo", 1), votes);
    }

    @Test
    void votes_singleChanged() {
        UUID voter = UUID.randomUUID();
        voteManager.vote(voter, "foo");
        voteManager.vote(voter, "bar");
        var votes = voteManager.votes();

        assertEquals(Map.of("bar", 1), votes);
    }

    @Test
    void votes_multiple() {
        voteManager.vote(UUID.randomUUID(), "foo");
        voteManager.vote(UUID.randomUUID(), "bar");
        voteManager.vote(UUID.randomUUID(), "foo");
        var votes = voteManager.votes();

        assertEquals(Map.of("foo", 2, "bar", 1), votes);
    }
}