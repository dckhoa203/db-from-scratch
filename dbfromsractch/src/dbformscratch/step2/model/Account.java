package dbformscratch.step2.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}
