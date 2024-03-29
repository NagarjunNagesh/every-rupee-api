package in.co.everyrupee.service.income;

import in.co.everyrupee.constants.GenericConstants;
import in.co.everyrupee.constants.income.DashboardConstants;
import in.co.everyrupee.events.income.OnFetchCategoryTotalCompleteEvent;
import in.co.everyrupee.events.income.OnSaveTransactionCompleteEvent;
import in.co.everyrupee.events.user.OnAffectBankAccountBalanceEvent;
import in.co.everyrupee.events.user.OnDeleteUserTransactionCompleteEvent;
import in.co.everyrupee.exception.InvalidAttributeValueException;
import in.co.everyrupee.exception.ResourceNotFoundException;
import in.co.everyrupee.pojo.RecurrencePeriod;
import in.co.everyrupee.pojo.TransactionType;
import in.co.everyrupee.pojo.income.Category;
import in.co.everyrupee.pojo.income.UserTransaction;
import in.co.everyrupee.pojo.user.BankAccount;
import in.co.everyrupee.repository.income.UserTransactionsRepository;
import in.co.everyrupee.service.user.BankAccountService;
import in.co.everyrupee.utils.ERStringUtils;
import in.co.everyrupee.utils.GenericUtils;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

@Transactional
@Service
@CacheConfig(cacheNames = {DashboardConstants.Transactions.TRANSACTIONS_CACHE_NAME})
public class UserTransactionService implements IUserTransactionService {

  @Autowired private UserTransactionsRepository userTransactionsRepository;

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private CategoryService categoryService;

  @Autowired private BankAccountService bankAccountService;

  Logger LOGGER = LoggerFactory.getLogger(this.getClass());

  /**
   * fetches User Transactions for a particular user
   *
   * @param formData
   * @return
   */
  @Override
  @Cacheable(key = "{#pFinancialPortfolioId,#dateMeantFor}")
  public Object fetchUserTransaction(String pFinancialPortfolioId, String dateMeantFor) {
    List<UserTransaction> userTransactions = new ArrayList<UserTransaction>();
    DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
    Date date = new Date();
    try {
      date = format.parse(dateMeantFor);
    } catch (ParseException e) {
      LOGGER.error(e + " Unable to add date to the user Transaction");
      return userTransactions;
    }

    userTransactions =
        userTransactionsRepository.findByFinancialPortfolioIdAndDate(pFinancialPortfolioId, date);

    if (CollectionUtils.isEmpty(userTransactions)) {
      LOGGER.warn("user transactions data is empty for user ");
      return userTransactions;
    }

    return sortByCategoryIdForHtmlTransactionsPage(userTransactions, pFinancialPortfolioId);
  }

  private Map<Integer, List<UserTransaction>> sortByCategoryIdForHtmlTransactionsPage(
      List<UserTransaction> userTransactions, String pFinancialPortfolioId) {
    Map<Integer, List<UserTransaction>> userTransactionsMap =
        new HashMap<Integer, List<UserTransaction>>();

    // Build a map with category and transactions
    for (UserTransaction userTransaction : userTransactions) {
      if (!userTransactionsMap.containsKey(userTransaction.getCategoryId())) {
        List<UserTransaction> list = new ArrayList<UserTransaction>();
        list.add(userTransaction);

        userTransactionsMap.put(userTransaction.getCategoryId(), list);
      } else {
        userTransactionsMap.get(userTransaction.getCategoryId()).add(userTransaction);
      }
    }

    LOGGER.debug("finished sorting for the financial portfolio - " + pFinancialPortfolioId);

    return userTransactionsMap;
  }

  /**
   * Save User Transaction to the database
   *
   * @param formData
   * @return
   */
  @Override
  @CacheEvict(key = "{#pFinancialPortfolioId,#formData.get(\"dateMeantFor\").get(0)}")
  public UserTransaction saveUserTransaction(
      MultiValueMap<String, String> formData, String pFinancialPortfolioId) {

    if (CollectionUtils.isEmpty(
        formData.get(DashboardConstants.Transactions.TRANSACTIONS_AMOUNT))) {
      throw new ResourceNotFoundException("UserTransactions", "formData", formData);
    }

    UserTransaction userTransaction = new UserTransaction();
    // Set Financial portfolio ID
    userTransaction.setFinancialPortfolioId(pFinancialPortfolioId);
    // Set description
    if (CollectionUtils.isNotEmpty(formData.get(DashboardConstants.Transactions.DESCRIPTION))) {
      userTransaction.setDescription(
          formData.get(DashboardConstants.Transactions.DESCRIPTION).get(0));
    }
    // Set Recurrence
    if (CollectionUtils.isNotEmpty(formData.get(DashboardConstants.Transactions.RECURRENCE))) {
      userTransaction.setRecurrence(
          RecurrencePeriod.valueOf(
              formData.get(DashboardConstants.Transactions.RECURRENCE).get(0)));
    }
    // Set Category ID
    userTransaction.setCategoryId(
        Integer.parseInt(formData.get(DashboardConstants.Transactions.CATEGORY_OPTIONS).get(0)));
    // Set ID
    userTransaction.setAmount(
        Double.parseDouble(
            formData.get(DashboardConstants.Transactions.TRANSACTIONS_AMOUNT).get(0)));

    String dateString = formData.get(DashboardConstants.Budget.DATE_MEANT_FOR).get(0);
    try {
      userTransaction.setDateMeantFor(
          new SimpleDateFormat(DashboardConstants.DATE_FORMAT).parse(dateString));
    } catch (ParseException e) {
      LOGGER.error(e + " Unable to add date to the user budget");
    }

    // Set the account ID as the selected bank account ID
    BankAccount bankAccount = bankAccountService.fetchSelectedAccount(pFinancialPortfolioId);
    userTransaction.setAccountId(bankAccount.getId());

    UserTransaction userTransactionResponse = userTransactionsRepository.save(userTransaction);

    executeEventListenersForSaveAction(
        formData, pFinancialPortfolioId, bankAccount, userTransactionResponse);

    return userTransactionResponse;
  }

  /**
   * Execute event listeners for save action
   *
   * @param formData
   * @param pFinancialPortfolioId
   * @param bankAccount
   * @param userTransactionResponse
   */
  @Async
  private void executeEventListenersForSaveAction(
      MultiValueMap<String, String> formData,
      String pFinancialPortfolioId,
      BankAccount bankAccount,
      UserTransaction userTransactionResponse) {
    // Auto Create Budget on saving the transaction
    boolean categoryIncome =
        categoryService.categoryIncome(userTransactionResponse.getCategoryId());
    // Fetch the transaction amount to affect the bank account balance
    double transactionAmount = userTransactionResponse.getAmount();
    if (categoryIncome) {
      eventPublisher.publishEvent(
          new OnSaveTransactionCompleteEvent(pFinancialPortfolioId, formData));
    } else {
      // If amount is expense then set amount modified to a negative value
      transactionAmount *= -1;
    }

    // Auto update the bankaccount balance
    eventPublisher.publishEvent(
        new OnAffectBankAccountBalanceEvent(bankAccount, transactionAmount, null));
  }

  /**
   * Deletes all the transactions with the id separated with commas
   *
   * @param transactionalIds
   * @return
   */
  @Override
  @CacheEvict(key = "{#financialPortfolioId,#dateMeantFor}")
  public void deleteUserTransactions(
      String transactionalIds, String financialPortfolioId, String dateMeantFor) {
    String[] arrayOfTransactionIds = transactionalIds.split(GenericConstants.COMMA);
    Set<String> transactionIdsAsSet = new HashSet<String>();
    transactionIdsAsSet.addAll(Arrays.asList(arrayOfTransactionIds));
    transactionIdsAsSet.remove(ERStringUtils.EMPTY);
    List<Integer> transactionIdsAsIntegerList =
        transactionIdsAsSet.stream()
            .filter(Objects::nonNull)
            .map(s -> Integer.parseInt(s))
            .collect(Collectors.toList());

    executeEventListenersForDeleteAction(transactionIdsAsIntegerList);

    userTransactionsRepository.deleteUsersWithIds(
        transactionIdsAsIntegerList, financialPortfolioId);
  }

  /**
   * Execute Event Listeners for delete action
   *
   * @param transactionIdsAsIntegerList
   */
  @Async
  private void executeEventListenersForDeleteAction(List<Integer> transactionIdsAsIntegerList) {
    // Fetch all user transactions
    List<UserTransaction> userTransList =
        userTransactionsRepository.findAllById(transactionIdsAsIntegerList);
    // Auto update the bank account balance
    eventPublisher.publishEvent(new OnDeleteUserTransactionCompleteEvent(userTransList));
  }

  /** Update the transactions with the new value */
  @Override
  @CacheEvict(key = "{#financialPortfolioId,#formData.get(\"dateMeantFor\").get(0)}")
  public UserTransaction updateTransactions(
      MultiValueMap<String, String> formData, String formFieldName, String financialPortfolioId) {

    Optional<UserTransaction> userTransaction =
        userTransactionsRepository.findById(
            Integer.parseInt(
                formData.get(DashboardConstants.Transactions.TRANSACTIONS__ID_JSON).get(0)));

    // Update Description
    if (ERStringUtils.equalsIgnoreCase(
        formFieldName, DashboardConstants.Transactions.DESCRIPTION)) {
      userTransaction
          .get()
          .setDescription(formData.get(DashboardConstants.Transactions.DESCRIPTION).get(0));
    }

    // Update Amount
    if (ERStringUtils.equalsIgnoreCase(
        formFieldName, DashboardConstants.Transactions.AMOUNT_FIELD_NAME)) {
      double newAmount =
          Double.parseDouble(
              formData.get(DashboardConstants.Transactions.TRANSACTIONS_AMOUNT).get(0));
      updateAmountInAccount(userTransaction, newAmount);
      userTransaction.get().setAmount(newAmount);
    }

    // Update Category
    if (ERStringUtils.equalsIgnoreCase(
        formFieldName, DashboardConstants.Transactions.CATEGORY_FORM_FIELD_NAME)) {
      userTransaction
          .get()
          .setCategoryId(
              Integer.parseInt(
                  formData.get(DashboardConstants.Transactions.CATEGORY_ID_JSON).get(0)));
    }

    // Update Recurrence
    if (ERStringUtils.equalsIgnoreCase(formFieldName, DashboardConstants.Transactions.RECURRENCE)) {
      userTransaction
          .get()
          .setRecurrence(
              RecurrencePeriod.valueOf(
                  formData.get(DashboardConstants.Transactions.RECURRENCE).get(0)));
    }

    // Update Account ID
    if (ERStringUtils.equalsIgnoreCase(formFieldName, DashboardConstants.Transactions.ACCOUNT_ID)) {
      int newAccountId =
          Integer.parseInt(formData.get(DashboardConstants.Transactions.ACCOUNT_ID).get(0));
      updateTransAmountForAccount(userTransaction, newAccountId);
      userTransaction.get().setAccountId(newAccountId);
    }

    UserTransaction userTransactionSaved = userTransactionsRepository.save(userTransaction.get());

    return userTransactionSaved;
  }

  /**
   * Remove transaction from old account
   *
   * @param userTransaction
   */
  @Async
  private void updateTransAmountForAccount(
      Optional<UserTransaction> userTransaction, int newAccountId) {
    double amountToModify = userTransaction.get().getAmount();
    // Auto Create Budget on saving the transaction
    boolean categoryIncome = categoryService.categoryIncome(userTransaction.get().getCategoryId());
    // While removing from account have a negative effect
    if (categoryIncome) {
      amountToModify *= -1;
    }
    // Auto update the bankaccount balance (Old Account)
    eventPublisher.publishEvent(
        new OnAffectBankAccountBalanceEvent(
            null, amountToModify, userTransaction.get().getAccountId()));
    // If category is income then reverse (-) or If category if expense then add (-)
    amountToModify *= -1;

    // Auto update the bankaccount balance (New Account)
    eventPublisher.publishEvent(
        new OnAffectBankAccountBalanceEvent(null, amountToModify, newAccountId));
  }

  /**
   * Update amount in account (ASYNC)
   *
   * @param userTransaction
   * @param newAmount
   * @return
   */
  @Async
  private void updateAmountInAccount(Optional<UserTransaction> userTransaction, double newAmount) {
    double oldAmount = userTransaction.get().getAmount();
    // Auto Create Budget on saving the transaction
    boolean categoryIncome = categoryService.categoryIncome(userTransaction.get().getCategoryId());
    if (categoryIncome) {
      oldAmount *= -1;
    } else {
      newAmount *= -1;
    }
    double amountToModify = oldAmount + newAmount;
    // Auto update the bankaccount balance (The old Amount has to be removed from
    // the bank account balance)
    eventPublisher.publishEvent(
        new OnAffectBankAccountBalanceEvent(
            null, amountToModify, userTransaction.get().getAccountId()));
  }

  /** Fetch category total */
  @Override
  public Map<Integer, Double> fetchCategoryTotalAndUpdateUserBudget(
      String financialPortfolioId, String dateMeantFor, boolean updateBudget) {

    Map<Integer, Double> categoryAndTotalAmountMap = new HashMap<Integer, Double>();
    DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
    Date date = new Date();
    try {
      date = format.parse(dateMeantFor);
    } catch (ParseException e) {
      LOGGER.error(e + " Unable to add date to the user transaction");
    }

    List<UserTransaction> userTransactions =
        userTransactionsRepository.findByFinancialPortfolioIdAndDate(financialPortfolioId, date);

    if (CollectionUtils.isEmpty(userTransactions)) {
      LOGGER.warn("user transactions data is empty for user ");
      return categoryAndTotalAmountMap;
    }

    for (UserTransaction userTransaction : userTransactions) {
      if (categoryAndTotalAmountMap.containsKey(userTransaction.getCategoryId())) {
        Double categoryTotalAmountPrevious =
            categoryAndTotalAmountMap.get(userTransaction.getCategoryId());
        categoryAndTotalAmountMap.put(
            userTransaction.getCategoryId(),
            categoryTotalAmountPrevious + userTransaction.getAmount());
      } else {
        categoryAndTotalAmountMap.put(userTransaction.getCategoryId(), userTransaction.getAmount());
      }
    }

    if (updateBudget) {
      // Auto Create Budget on saving the transaction
      eventPublisher.publishEvent(
          new OnFetchCategoryTotalCompleteEvent(
              categoryAndTotalAmountMap, dateMeantFor, financialPortfolioId));
    }

    return categoryAndTotalAmountMap;
  }

  /** BUDGET: Fetch category total */
  @Override
  public Double fetchUserTransactionCategoryTotal(
      String financialPortfolioId, Integer categoryId, Date dateMeantFor) {

    Double categoryTotal = 0.0d;

    List<UserTransaction> userTransactions =
        userTransactionsRepository.fetchUserTransactionWithCategoryId(
            categoryId, financialPortfolioId, dateMeantFor);

    categoryTotal =
        userTransactions.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.summingDouble(UserTransaction::getAmount));

    return categoryTotal;
  }

  /** OVERVIEW: Fetches the user transactions with by creation date (WITHOUT SORT) */
  @Override
  public List<UserTransaction> fetchUserTransactionByCreationDate(
      String financialPortfolioId, String dateMeantFor) {

    if (financialPortfolioId == null) {
      throw new InvalidAttributeValueException(
          "fetchUserTransactionByCreationDate", "financialPortfolioId", financialPortfolioId);
    }

    List<UserTransaction> userTransactions = new ArrayList<UserTransaction>();
    DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
    Date date = new Date();
    try {
      date = format.parse(dateMeantFor);
    } catch (ParseException e) {
      LOGGER.error(e + " Unable to add date to the user Transaction");
      return userTransactions;
    }
    userTransactions =
        userTransactionsRepository.findByFinancialPortfolioIdAndDate(financialPortfolioId, date);

    if (CollectionUtils.isEmpty(userTransactions)) {
      LOGGER.warn("user transactions data is empty for user ");
    }

    return userTransactions;
  }

  /** OVERVIEW: Fetch lifetime calculations */
  @Override
  public Object fetchLifetimeCalculations(
      TransactionType type, boolean fetchAverage, String pfinancialPortfolioId) {

    if (pfinancialPortfolioId == null) {
      throw new InvalidAttributeValueException(
          "fetchUserTransactionByCreationDate", "financialPortfolioId", pfinancialPortfolioId);
    }

    switch (type) {
      case INCOME:
        return calculateLifetimeForExpenseOrIncome(
            fetchAverage, pfinancialPortfolioId, DashboardConstants.Category.INCOME_CATEGORY_ID);
      case EXPENSE:
        return calculateLifetimeForExpenseOrIncome(
            fetchAverage, pfinancialPortfolioId, DashboardConstants.Category.EXPENSE_CATEGORY_ID);
      default:
        LOGGER.error("fetchLifetimeCalculations: TransactionType is not mapped to the ENUM class");
        break;
    }
    return null;
  }

  /**
   * Calculate the lifetime expense with average or sends a list of all the income over a span of a
   * year
   *
   * @param fetchAverage
   * @param pFinancialPortfolioId
   */
  private Object calculateLifetimeForExpenseOrIncome(
      boolean fetchAverage, String pFinancialPortfolioId, String parentCategoryId) {

    List<Category> categories = categoryService.fetchCategories();

    // Fetch all the categories that match the current parent category
    List<Integer> currentCategories =
        categories.stream()
            .map(
                categoryItem -> {
                  if (ERStringUtils.equalsIgnoreCase(
                      categoryItem.getParentCategory(), parentCategoryId)) {
                    return categoryItem.getCategoryId();
                  }
                  return -1;
                })
            .collect(Collectors.toList());

    // Remove all occurrences of -1
    currentCategories = GenericUtils.removeAll(currentCategories, -1);

    List<UserTransaction> lifetimeTransactions =
        userTransactionsRepository.findByFinancialPortfolioIdAndCategories(
            pFinancialPortfolioId, currentCategories);

    // If the transaction is empty then return null
    if (CollectionUtils.isEmpty(lifetimeTransactions)) {
      return null;
    }

    // If fetch average then calculate average
    if (fetchAverage) {
      return fetchAverageAmount(lifetimeTransactions);
    } else {
      return fetchOneYearData(lifetimeTransactions);
    }
  }

  /**
   * Fetch one year data for income or expense
   *
   * @param lifetimeTransactions
   * @return
   */
  private Object fetchOneYearData(List<UserTransaction> lifetimeTransactions) {
    // Fetch all the unique dates
    Map<Date, Double> dateAndAmountAsList = new HashMap<Date, Double>();

    // Map of Date and Sum of all the transaction amounts and sorts by the
    // date meant for (TREEMAP sorts the map by the key)
    dateAndAmountAsList =
        lifetimeTransactions.stream()
            .collect(
                Collectors.groupingBy(
                    UserTransaction::getDateMeantFor,
                    TreeMap::new,
                    Collectors.summingDouble(UserTransaction::getAmount)));

    return dateAndAmountAsList;
  }

  /*
   * * Fetch average amount parent category
   *
   * @param lifetimeTransactions
   *
   * @return
   */
  private Object fetchAverageAmount(List<UserTransaction> lifetimeTransactions) {

    // Fetch all the unique dates
    Set<Date> dateMeantForSet =
        lifetimeTransactions.stream()
            .map(
                userTransaction -> {
                  return userTransaction.getDateMeantFor();
                })
            .collect(Collectors.toSet());

    // Calculate the total income
    Double incomeTotal =
        lifetimeTransactions.stream()
            .mapToDouble(userTransaction -> userTransaction.getAmount())
            .sum();

    if (dateMeantForSet.size() == 0) {
      return 0d;
    } else {
      return (incomeTotal / dateMeantForSet.size());
    }
  }

  /**
   * Delete All user transactions
   *
   * @param pFinancialPortfolioId
   */
  @Override
  @CacheEvict(value = DashboardConstants.Transactions.TRANSACTIONS_CACHE_NAME, allEntries = true)
  public void deleteUserTransactions(String pFinancialPortfolioId) {
    userTransactionsRepository.deleteAllUserTransactions(pFinancialPortfolioId);
  }

  /**
   * Delete all transactions by bank account
   *
   * @param bankAccountbyId
   */
  @Override
  @CacheEvict(value = DashboardConstants.Transactions.TRANSACTIONS_CACHE_NAME, allEntries = true)
  public void deleteTransactionsByBankAccount(int bankAccountById) {
    userTransactionsRepository.deleteByBankAccount(bankAccountById);
  }

  /** Copy transactions from previous Months */
  @Override
  @CacheEvict(value = DashboardConstants.Transactions.TRANSACTIONS_CACHE_NAME, allEntries = true)
  public void copyFromPreviousMonth() {

    LocalDate previousMonthSameDay = LocalDate.now().minus(1, ChronoUnit.MONTHS).withDayOfMonth(1);
    Date previousMonthsDate =
        Date.from(previousMonthSameDay.atStartOfDay(ZoneId.systemDefault()).toInstant());

    List<UserTransaction> userTransactions =
        userTransactionsRepository.findRecurringTransactions(
            previousMonthsDate, RecurrencePeriod.MONTHLY);

    // If the transaction is empty then return null
    if (CollectionUtils.isEmpty(userTransactions)) {
      return;
    }

    // Create current date
    LocalDate currentLocalDate = LocalDate.now().withDayOfMonth(1);
    Date currentDate = Date.from(currentLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    // Create new user transactions from previous months
    List<UserTransaction> newUserTransactions = new ArrayList<UserTransaction>();
    for (UserTransaction previousUserTransaction : userTransactions) {
      UserTransaction newUserTransaction = new UserTransaction();
      newUserTransaction.setAmount(previousUserTransaction.getAmount());
      newUserTransaction.setCategoryId(previousUserTransaction.getCategoryId());
      newUserTransaction.setDateMeantFor(currentDate);
      newUserTransaction.setDescription(previousUserTransaction.getDescription());
      newUserTransaction.setFinancialPortfolioId(previousUserTransaction.getFinancialPortfolioId());
      newUserTransaction.setRecurrence(previousUserTransaction.getRecurrence());
      newUserTransaction.setAccountId(previousUserTransaction.getAccountId());
      newUserTransactions.add(newUserTransaction);
    }

    userTransactionsRepository.saveAll(newUserTransactions);
  }
}
