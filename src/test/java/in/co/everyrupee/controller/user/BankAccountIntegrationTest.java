package in.co.everyrupee.controller.user;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.co.everyrupee.constants.income.DashboardConstants;
import in.co.everyrupee.constants.user.BankAccountConstants;
import in.co.everyrupee.pojo.user.AccountType;
import in.co.everyrupee.pojo.user.BankAccount;
import in.co.everyrupee.repository.user.BankAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Bank Account Controller Test (Cache, Controller. Service)
 *
 * @author Nagarjun
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class BankAccountIntegrationTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mvc;

  private static final String FINANCIAL_PORTFOLIO_ID = "193000000";

  private List<BankAccount> allBankAccounts;

  @MockBean private BankAccountRepository bankAccountRepository;

  @Autowired CacheManager cacheManager;

  private final ObjectMapper objectMapper = new ObjectMapper();

  Logger logger = LoggerFactory.getLogger(this.getClass());

  @Before
  public void setUp() {
    setMvc(MockMvcBuilders.webAppContextSetup(getContext()).apply(springSecurity()).build());

    // Build data
    BankAccount bankAccount2 = new BankAccount();
    bankAccount2.setFinancialPortfolioId(FINANCIAL_PORTFOLIO_ID);
    bankAccount2.setLinked(true);
    bankAccount2.setBankAccountName("ABCD");
    bankAccount2.setNumberOfTimesSelected(100);
    bankAccount2.setAccountBalance(324);
    bankAccount2.setAccountType(AccountType.CASH);

    BankAccount bankAccount = new BankAccount();
    bankAccount.setFinancialPortfolioId(FINANCIAL_PORTFOLIO_ID);
    bankAccount.setLinked(false);
    bankAccount.setBankAccountName("EFGH");
    bankAccount.setNumberOfTimesSelected(1);
    bankAccount.setAccountBalance(100);
    bankAccount.setAccountType(AccountType.CREDITCARD);

    setAllBankAccounts(new ArrayList<BankAccount>());
    getAllBankAccounts().add(bankAccount);
    getAllBankAccounts().add(bankAccount2);

    // Testing the Cache Layer
    when(getBankAccountRepository().findByFinancialPortfolioId(FINANCIAL_PORTFOLIO_ID))
        .thenReturn(getAllBankAccounts());

    // Mock the save to return some value
    when(bankAccountRepository.save(Mockito.any(BankAccount.class))).thenReturn(bankAccount2);

    // Mock find all
    when(bankAccountRepository.findAll()).thenReturn(getAllBankAccounts());
  }

  /**
   * TEST: Get all bank accounts
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void fetchAllBankAccounts() throws Exception {
    getMvc()
        .perform(
            get("/api/bankaccount/")
                .contentType(MediaType.APPLICATION_JSON)
                .param(
                    DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                    FINANCIAL_PORTFOLIO_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").isNotEmpty())
        // .andDo(MockMvcResultHandlers.print())
        .andExpect(jsonPath("$[0].accountBalance", is(100.0)));

    // Testing the Cache Layer
    getMvc()
        .perform(
            get("/api/bankaccount/")
                .contentType(MediaType.APPLICATION_JSON)
                .param(
                    DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                    FINANCIAL_PORTFOLIO_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").isNotEmpty())
        .andExpect(jsonPath("$[0].accountBalance", is(100.0)));

    // Making Sure the Cache was used
    verify(getBankAccountRepository(), times(1)).findByFinancialPortfolioId(FINANCIAL_PORTFOLIO_ID);
    // Ensuring that the cache contains the said values
    assertThat(
        getCacheManager()
            .getCache(BankAccountConstants.BANK_ACCOUNT_CACHE)
            .get(FINANCIAL_PORTFOLIO_ID),
        is(notNullValue()));
  }

  /**
   * TEST: Fetch all bank accounts
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void previewBankAccounts() throws Exception {
    getMvc()
        .perform(
            get("/api/bankaccount/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .param(
                    DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                    FINANCIAL_PORTFOLIO_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").isNotEmpty())
        .andExpect(jsonPath("$[0].accountBalance", is(324.0)));
  }

  /**
   * TEST: Fetch all bank accounts
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void selectAccount() throws Exception {

    RequestBuilder request =
        MockMvcRequestBuilders.post("/api/bankaccount/select")
            .param("id", "3232")
            .param("selectedAccount", "false")
            .param(
                DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                FINANCIAL_PORTFOLIO_ID.toString())
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);

    getMvc().perform(request).andExpect(status().isOk());
  }

  /**
   * TEST: Fetch all bank accounts
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void addAccount() throws Exception {

    RequestBuilder request =
        MockMvcRequestBuilders.post("/api/bankaccount/add")
            .param("id", "3232")
            .param("selectedAccount", "false")
            .param("linked", "false")
            .param("userId", "3233")
            .param("bankAccountName", "HDFC")
            .param(
                DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                FINANCIAL_PORTFOLIO_ID.toString())
            .param("accountBalance", "5655")
            .param("accountType", "CASH")
            .param("userId", "123456")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);

    getMvc()
        .perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isNotEmpty())
        .andExpect(jsonPath("$.accountBalance", is(324.0)));
  }

  /**
   * TEST: categorize all bank accounts
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void categorizeBankAccounts() throws Exception {
    getMvc()
        .perform(
            get("/api/bankaccount/categorize")
                .contentType(MediaType.APPLICATION_JSON)
                .param(
                    DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                    FINANCIAL_PORTFOLIO_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isNotEmpty());
  }

  /**
   * TEST: delete all bank accounts
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void deleteBankAccounts() throws Exception {
    getMvc()
        .perform(
            delete("/api/bankaccount/")
                .contentType(MediaType.APPLICATION_JSON)
                .param(
                    DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                    FINANCIAL_PORTFOLIO_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isNotEmpty());

    verify(getBankAccountRepository(), times(1)).deleteAllBankAccounts(Mockito.anyString());
  }

  /**
   * TEST: patch a bank account
   *
   * @throws Exception
   */
  @Test
  @WithMockUser(value = "spring")
  public void patchThisBankAccount() throws Exception {
    BankAccount bankAccount = new BankAccount();
    bankAccount.setAccountBalance(12);
    bankAccount.setFinancialPortfolioId(FINANCIAL_PORTFOLIO_ID);

    // Testing the Cache Layer
    when(getBankAccountRepository().findById(12345)).thenReturn(Optional.of(bankAccount));

    getMvc()
        .perform(
            patch("/api/bankaccount/12345")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(bankAccount)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isNotEmpty());

    verify(getBankAccountRepository(), times(1)).findById(Mockito.anyInt());
    verify(getBankAccountRepository(), times(1)).save(Mockito.any(BankAccount.class));
    // Ensuring that the cache contains the said values
    assertThat(
        getCacheManager()
            .getCache(BankAccountConstants.BANK_ACCOUNT_CACHE)
            .get(FINANCIAL_PORTFOLIO_ID),
        is(nullValue()));
  }

  /**
   * TEST: delete a bank account
   *
   * @throws Exception
   */
  @Test
  public void deleteThisBankAccount() throws Exception {

    getMvc()
        .perform(
            delete("/api/bankaccount/12345")
                .contentType(MediaType.APPLICATION_JSON)
                .param(
                    DashboardConstants.BankAccount.FINANCIAL_PORTFOLIO_ID,
                    FINANCIAL_PORTFOLIO_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isNotEmpty());

    verify(getBankAccountRepository(), times(1)).deleteById(12345);
  }

  private WebApplicationContext getContext() {
    return context;
  }

  private void setMvc(MockMvc mvc) {
    this.mvc = mvc;
  }

  private BankAccountRepository getBankAccountRepository() {
    return bankAccountRepository;
  }

  private MockMvc getMvc() {
    return mvc;
  }

  private void setAllBankAccounts(List<BankAccount> allBankAccounts) {
    this.allBankAccounts = allBankAccounts;
  }

  private List<BankAccount> getAllBankAccounts() {
    return allBankAccounts;
  }

  private CacheManager getCacheManager() {
    return cacheManager;
  }
}
