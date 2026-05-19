package cz.fel.cvut.beevidence_and_cyber;

import org.junit.jupiter.api.Test;

class BeEvidenceAndCyberApplicationTests {

    @Test
    public void givenApplicationClass_whenReferenced_thenReturnApplicationClass() {
        org.assertj.core.api.Assertions.assertThat(BeEvidenceAndCyberApplication.class).isNotNull();
    }
}
