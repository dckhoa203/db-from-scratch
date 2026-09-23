package dbformscratch.step3.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}