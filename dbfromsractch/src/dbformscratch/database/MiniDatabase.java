package dbformscratch.database;

import dbformscratch.model.Account;

import java.util.HashMap;
import java.util.Map;

public class MiniDatabase {

    private final Map<Long, Account> accounts = new HashMap<>();

    public void insert(Account account){
        accounts.put(account.id(), account);
    }

    public Account select(long id){
        return accounts.get(id);
    }

    public void update(Account account){
        accounts.put(account.id(), account);
    }

    public void delete(long id) {
        accounts.remove(id);
    }
}
