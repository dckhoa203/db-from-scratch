package dbformscratch.step4.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}
