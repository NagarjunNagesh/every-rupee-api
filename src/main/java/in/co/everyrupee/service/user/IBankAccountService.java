package in.co.everyrupee.service.user;

import in.co.everyrupee.pojo.user.AccountCategories;
import in.co.everyrupee.pojo.user.BankAccount;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import org.springframework.util.MultiValueMap;

public interface IBankAccountService {
  /**
   * Fetch all bank accounts
   *
   * @param financialPortfolioId
   * @return
   */
  public List<BankAccount> getAllBankAccounts(String financialPortfolioId);

  /**
   * Add a new bank account
   *
   * @param formData
   * @return
   */
  public BankAccount addNewBankAccount(MultiValueMap<String, String> formData);

  /**
   * Preview bank accounts (first three + default)
   *
   * @param financialPortfolioId
   * @return
   */
  public List<BankAccount> previewBankAccounts(String financialPortfolioId);

  /**
   * Select Account
   *
   * @param pFinancialPortfolioId
   * @param formData
   * @return
   */
  public void selectAccount(MultiValueMap<String, String> formData);

  /**
   * Categorize Bank Account
   *
   * @param pFinancialPortfolioId
   * @return
   */
  public Map<String, Set<BankAccount>> categorizeBankAccount(String pFinancialPortfolioId);

  /**
   * Delete All Bank Accounts
   *
   * @param pFinancialPortfolioId
   * @return
   */
  public void deleteAllBankAccounts(String pFinancialPortfolioId);

  /**
   * Fetch the selected Account
   *
   * @param pFinancialPortfolioId
   * @return
   */
  public BankAccount fetchSelectedAccount(String pFinancialPortfolioId);

  /**
   * Update Bank account balance
   *
   * @param bankAccount
   * @param amountModified
   */
  public void updateBankBalance(BankAccount bankAccount, Double amountModified);

  /**
   * Fetch Bank account by ID
   *
   * @param accountId
   * @return
   */
  public Optional<BankAccount> fetchBankAccountById(Integer accountId);

  /**
   * Fetch All Bank account
   *
   * @param accountIds
   * @return
   */
  List<BankAccount> fetchAllBankAccount(Set<Integer> accountIds);

  /**
   * Save All Bank Account
   *
   * @param bankAccountList
   */
  public void saveAll(List<BankAccount> bankAccountList);

  /**
   * Update Bank Account
   *
   * @param bankAccountId
   * @param formData
   * @return
   */
  public BankAccount updateBankAccount(String bankAccountId, BankAccount bankAccount);

  /**
   * Delete bank account
   *
   * @param pBankAccountId
   * @param pFinancialPortfolioId
   */
  public void deleteBankAccount(
      @Size(min = 0, max = 60) String pBankAccountId,
      @Size(min = 0, max = 60) String pFinancialPortfolioId);

  /**
   * Calculate account total
   *
   * @param accountCategories
   * @param pFinancialPortfolioId
   * @param fetchAverage
   * @return
   */
  public Object calculateTotal(
      @Valid Optional<AccountCategories> accountCategories,
      @Size(min = 0, max = 60) String pFinancialPortfolioId,
      boolean fetchAverage);
}
