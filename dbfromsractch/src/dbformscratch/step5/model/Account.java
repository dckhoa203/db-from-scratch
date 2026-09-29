package dbformscratch.step5.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}
