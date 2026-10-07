package dbformscratch.step6.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}
