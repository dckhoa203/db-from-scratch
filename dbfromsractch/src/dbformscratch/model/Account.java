package dbformscratch.model;

public record Account(
        long id,
        String accountNumber,
        long balance
) {
}
