package dbformscratch.step1.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}
