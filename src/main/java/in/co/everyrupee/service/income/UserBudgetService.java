package in.co.everyrupee.service.income;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import com.sun.org.slf4j.internal.LoggerFactory;

import in.co.everyrupee.constants.GenericConstants;
import in.co.everyrupee.constants.income.DashboardConstants;
import in.co.everyrupee.exception.InvalidAttributeValueException;
import in.co.everyrupee.exception.ResourceAlreadyPresentException;
import in.co.everyrupee.exception.ResourceNotFoundException;
import in.co.everyrupee.pojo.income.UserBudget;
import in.co.everyrupee.repository.income.UserBudgetRepository;
import in.co.everyrupee.utils.ERStringUtils;

@Transactional
@Service
@CacheConfig(cacheNames = { DashboardConstants.Budget.BUDGET_CACHE_NAME })
public class UserBudgetService implements IUserBudgetService {

	@Autowired
	private UserBudgetRepository userBudgetRepository;

	@Autowired
	private IUserTransactionService userTransactionService;

	Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 
	 * Fetches all the user budget with financial portfolio
	 * 
	 */
	@Override
	@Cacheable(key = "{#pFinancialPortfolioId, #dateMeantFor}")
	public List<UserBudget> fetchAllUserBudget(String pFinancialPortfolioId, String dateMeantFor) {
		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		List<UserBudget> userBudgetList = new ArrayList<UserBudget>();
		Date date;
		try {
			date = format.parse(dateMeantFor);

			userBudgetList = getUserBudgetRepository().fetchAllUserBudget(pFinancialPortfolioId, date);

			if (CollectionUtils.isEmpty(userBudgetList)) {
				logger.warn("user budgets data is empty for financial portfolio ", pFinancialPortfolioId);
			}

		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}

		return userBudgetList;
	}

	/**
	 * 
	 * Fetch user budget by category Ids
	 * 
	 */
	@Override
	public List<UserBudget> fetchAutoGeneratedUserBudgetByCategoryIds(String categoryIds, String pFinancialPortfolioId,
			String dateMeantFor) {

		String[] arrayOfCategoryIds = categoryIds.split(GenericConstants.COMMA);
		Set<String> categoryIdsAsSet = new HashSet<String>();
		categoryIdsAsSet.addAll(Arrays.asList(arrayOfCategoryIds));
		categoryIdsAsSet.remove(ERStringUtils.EMPTY);
		List<Integer> budgetIdsAsIntegerList = categoryIdsAsSet.stream().filter(Objects::nonNull)
				.map(s -> Integer.parseInt(s)).collect(Collectors.toList());

		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		List<UserBudget> userBudgetList = new ArrayList<UserBudget>();
		Date date;
		try {
			date = format.parse(dateMeantFor);

			userBudgetList = getUserBudgetRepository()
					.fetchAutoGeneratedUserBudgetWithCategoryIds(budgetIdsAsIntegerList, pFinancialPortfolioId, date);

		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}

		if (CollectionUtils.isEmpty(userBudgetList)) {
			logger.warn("user budgets data is empty for financialPortfoilio ", pFinancialPortfolioId);
		}

		return userBudgetList;
	}

	/**
	 * Save User Budget to the database if not present, updates it if it is present
	 * 
	 * formData.get(DashboardConstants.Budget.AUTO_GENERATED_JSON)
	 * 
	 * @param formData
	 * @return
	 */
	@Override
	@CacheEvict(key = "{#pFinancialPortfolioId, #formData.getFirst(\"dateMeantFor\")}")
	public UserBudget saveAutoGeneratedUserBudget(MultiValueMap<String, String> formData,
			String pFinancialPortfolioId) {

		String dateString = formData.get(DashboardConstants.Budget.DATE_MEANT_FOR).get(0);
		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		Integer categoryId = Integer.parseInt(formData.getFirst(DashboardConstants.Budget.CATEGORY_ID));
		Long budgetIdL = Long.parseLong(pFinancialPortfolioId)
				+ Integer.parseInt(formData.getFirst(DashboardConstants.Budget.DATE_MEANT_FOR)) + categoryId;
		Date date;

		// If the auto generated is false then update the budget or create a new one
		// (USER BUDGET CONTROLLER ONLY ACCESS)
		if (CollectionUtils.isNotEmpty(formData.get(DashboardConstants.Budget.AUTO_GENERATED_JSON))
				&& ERStringUtils.isNotEmpty(formData.get(DashboardConstants.Budget.AUTO_GENERATED_JSON).get(0))
				&& (ERStringUtils.equalsIgnoreCase(formData.get(DashboardConstants.Budget.AUTO_GENERATED_JSON).get(0),
						DashboardConstants.BOOLEAN_FALSE))) {
			return saveUserBudget(formData, pFinancialPortfolioId);
		}

		if (CollectionUtils.isEmpty(formData.get(DashboardConstants.Budget.PLANNED))
				|| ERStringUtils.isEmpty(formData.get(DashboardConstants.Budget.PLANNED).get(0))) {
			throw new ResourceNotFoundException("UserBudget", "planned", "empty");
		}

		Optional<UserBudget> categoryBudget = getUserBudgetRepository().findById(new BigInteger(budgetIdL.toString()));
		try {
			date = format.parse(dateString);
			logger.debug("Budget for the category is empty " + categoryBudget.isPresent()
					+ " Financial Portfolio Id is " + pFinancialPortfolioId);
			/**
			 * Update user budget if already present
			 */
			if (!categoryBudget.isPresent()) {
				return createUserBudget(formData, pFinancialPortfolioId, true, date);
			} else if (categoryBudget.get().getAutoGeneratedBudget()) {
				// Update the user budget if the auto generated is true
				categoryBudget.get().setPlanned(getUserTransactionService()
						.fetchUserTransactionCategoryTotal(pFinancialPortfolioId, categoryId, date));
				return getUserBudgetRepository().save(categoryBudget.get());
			}

		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}

		return null;
	}

	/**
	 * Create a new user budget
	 * 
	 * @param formData
	 * @param pFinancialPortfolioId
	 * @param autoGenerated
	 * @param date
	 * @return
	 */
	private UserBudget createUserBudget(MultiValueMap<String, String> formData, String pFinancialPortfolioId,
			boolean autoGenerated, Date date) {
		UserBudget userBudgetSaved;
		// Create budget otherwise
		UserBudget userBudget = new UserBudget();
		// Prepare Budget Id
		String categoryId = formData.getFirst(DashboardConstants.Budget.CATEGORY_ID);
		Long budgetIdL = Long.parseLong(pFinancialPortfolioId)
				+ Integer.parseInt(formData.getFirst(DashboardConstants.Budget.DATE_MEANT_FOR))
				+ Integer.parseInt(categoryId);
		BigInteger budgetId = new BigInteger(budgetIdL.toString());
		userBudget.setBudgetId(budgetId);
		userBudget.setFinancialPortfolioId(pFinancialPortfolioId);
		userBudget.setCategoryId(Integer.parseInt(categoryId));
		userBudget.setPlanned(Double.parseDouble(formData.get(DashboardConstants.Budget.PLANNED).get(0)));
		userBudget.setAutoGeneratedBudget(autoGenerated);
		userBudget.setDateMeantFor(date);
		userBudgetSaved = getUserBudgetRepository().save(userBudget);
		return userBudgetSaved;
	}

	/**
	 * 
	 * Delete user budgets with category id
	 * 
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #dateMeantFor}")
	public void deleteAutoGeneratedUserBudgets(String categoryIds, String financialPortfolioId, String dateMeantFor) {
		String[] arrayOfCategoryIds = categoryIds.split(GenericConstants.COMMA);
		Set<String> categoryIdsAsSet = new HashSet<String>();
		categoryIdsAsSet.addAll(Arrays.asList(arrayOfCategoryIds));
		categoryIdsAsSet.remove(ERStringUtils.EMPTY);
		List<Integer> categoryIdsAsIntegerList = categoryIdsAsSet.stream().filter(Objects::nonNull)
				.map(s -> Integer.parseInt(s)).collect(Collectors.toList());

		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		Date date;
		try {
			date = format.parse(dateMeantFor);

			getUserBudgetRepository().deleteAutoGeneratedUserBudgetWithCategoryIds(categoryIdsAsIntegerList,
					financialPortfolioId, date);
		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}
	}

	/**
	 * 
	 * Delete user budgets with category id
	 * 
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #dateMeantFor}")
	public void deleteAllUserBudgets(String financialPortfolioId, String dateMeantFor, Boolean autoGenerated) {

		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		Date date;
		try {
			date = format.parse(dateMeantFor);

			if (autoGenerated != null) {
				getUserBudgetRepository().deleteAllUserBudget(financialPortfolioId, date, autoGenerated);
			} else {
				getUserBudgetRepository().deleteAllUserBudget(financialPortfolioId, date);
			}
		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}

	}

	/**
	 * 
	 * Updates the amount to the budget. The amount is the total increase in the
	 * budget.
	 * 
	 * ADDS THE BUDGET AMOUNT WITH THE ONES FROM THE DATABASE
	 * 
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #dateMeantFor}")
	public List<UserBudget> updateAutoGeneratedBudget(MultiValueMap<String, String> formData, String formFieldName,
			String financialPortfolioId, String dateMeantFor) {

		if (MapUtils.isEmpty(formData) || CollectionUtils.isEmpty(formData.keySet())) {
			return null;
		}

		if (ERStringUtils.notEqualsIgnoreCase(formFieldName, DashboardConstants.Budget.AUTO_GENERATED_JSON)) {
			return null;
		}

		List<Integer> categoryIdsAsIntegerList = formData.keySet().stream().filter(Objects::nonNull)
				.map(s -> Integer.parseInt(s)).collect(Collectors.toList());

		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		List<UserBudget> userBudgetSaved = new ArrayList<UserBudget>();
		Date date;
		try {
			date = format.parse(dateMeantFor);

			List<UserBudget> userBudgetList = getUserBudgetRepository()
					.fetchAutoGeneratedUserBudgetWithCategoryIds(categoryIdsAsIntegerList, financialPortfolioId, date);

			Map<Integer, Double> categoryIdAndCategoryTotalMap = getUserTransactionService()
					.fetchCategoryTotalAndUpdateUserBudget(financialPortfolioId, dateMeantFor, false);

			for (UserBudget userBudget : userBudgetList) {
				userBudget.setPlanned(categoryIdAndCategoryTotalMap.get(userBudget.getCategoryId()));
			}

			userBudgetSaved = getUserBudgetRepository().saveAll(userBudgetList);

		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}
		return userBudgetSaved;

	}

	/**
	 * Update Autogenerated Userbudget with the total amount, Need date as string
	 * for cache
	 * 
	 * REPLACES THE BUDGET AMOUNT WITH THE ONES FROM THE DATABASE
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #dateMeantFor}")
	public void updateAutoGeneratedUserBudget(String financialPortfolioId,
			Map<Integer, Double> categoryIdAndCategoryTotal, String dateMeantFor) {
		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		Date date;
		try {
			date = format.parse(dateMeantFor);

			List<UserBudget> userBudgetList = getUserBudgetRepository().fetchAutoGeneratedUserBudgetWithCategoryIds(
					categoryIdAndCategoryTotal.keySet().stream().collect(Collectors.toList()), financialPortfolioId,
					date);

			for (UserBudget userBudget : userBudgetList) {
				userBudget.setPlanned(categoryIdAndCategoryTotal.get(userBudget.getCategoryId()));
			}

			getUserBudgetRepository().saveAll(userBudgetList);
		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}
	}

	/**
	 * Save User Budget to the database if not present, updates it if it is present
	 * (auto generated = FALSE)
	 * 
	 * @param formData
	 * @return
	 */
	@CacheEvict(key = "{#pFinancialPortfolioId, #formData.get(\"dateMeantFor\").get(0)}")
	private UserBudget saveUserBudget(MultiValueMap<String, String> formData, String pFinancialPortfolioId) {
		if (CollectionUtils.isEmpty(formData.get(DashboardConstants.Budget.PLANNED))
				|| ERStringUtils.isEmpty(formData.get(DashboardConstants.Budget.PLANNED).get(0))) {
			throw new ResourceNotFoundException("UserBudget", "planned", "empty");
		}

		String dateString = formData.get(DashboardConstants.Budget.DATE_MEANT_FOR).get(0);
		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		UserBudget userBudgetSaved = null;
		Date date;
		try {
			date = format.parse(dateString);

			String categoryId = formData.get(DashboardConstants.Budget.CATEGORY_ID).get(0);
			List<Integer> categoryIds = new ArrayList<Integer>();
			categoryIds.add(Integer.parseInt(categoryId));
			List<UserBudget> categoryBudget = getUserBudgetRepository().fetchUserBudgetWithCategoryIds(categoryIds,
					pFinancialPortfolioId, date);

			/**
			 * Update user budget if already present
			 */
			if (CollectionUtils.isNotEmpty(categoryBudget) && categoryBudget.get(0) != null) {

				Double plannedAmount = Double.parseDouble(formData.get(DashboardConstants.Budget.PLANNED).get(0));
				categoryBudget.get(0).setPlanned(plannedAmount);
				categoryBudget.get(0).setAutoGeneratedBudget(false);
				userBudgetSaved = getUserBudgetRepository().save(categoryBudget.get(0));
				return userBudgetSaved;
			}

			userBudgetSaved = createUserBudget(formData, pFinancialPortfolioId, false, date);

		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}

		return userBudgetSaved;
	}

	/**
	 * Deletes All User Budgets irrespective of auto generation
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #dateMeantFor}")
	public void deleteUserBudgets(String categoryIds, String financialPortfolioId, String dateMeantFor) {
		String[] arrayOfCategoryIds = categoryIds.split(GenericConstants.COMMA);
		Set<String> categoryIdsAsSet = new HashSet<String>();
		categoryIdsAsSet.addAll(Arrays.asList(arrayOfCategoryIds));
		categoryIdsAsSet.remove(ERStringUtils.EMPTY);
		List<Integer> categoryIdsAsIntegerList = categoryIdsAsSet.stream().filter(Objects::nonNull)
				.map(s -> Integer.parseInt(s)).collect(Collectors.toList());

		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		Date date;
		try {
			date = format.parse(dateMeantFor);

			getUserBudgetRepository().deleteUserBudgetWithCategoryIds(categoryIdsAsIntegerList, financialPortfolioId,
					date);
		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}

	}

	/**
	 * Copy the previous budget mentioned by the system to the period mentioned by
	 * the user
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #formData.get(\"dateMeantFor\").get(0)}")
	public List<UserBudget> copyPreviousBudgetToSelectedMonth(String financialPortfolioId,
			MultiValueMap<String, String> formData) {

		List<UserBudget> copiedUserBudgets = new ArrayList<UserBudget>();
		String dateToCopy = formData.get(DashboardConstants.Budget.DATE_TO_COPY).get(0);
		String dateMeantFor = formData.get(DashboardConstants.Budget.DATE_MEANT_FOR).get(0);
		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);
		List<UserBudget> userBudgetList = new ArrayList<UserBudget>();

		try {
			Calendar calendarDateToCopy = Calendar.getInstance();
			Calendar calendarDateMeantFor = Calendar.getInstance();
			Date dateToCopyAsDate = format.parse(dateToCopy);
			Date dateMeantForAsDate = format.parse(dateMeantFor);
			calendarDateToCopy.setTime(dateToCopyAsDate);
			calendarDateMeantFor.setTime(dateMeantForAsDate);

			// if the month and year match.
			if ((calendarDateMeantFor.get(Calendar.MONTH) == calendarDateToCopy.get(Calendar.MONTH))
					&& (calendarDateMeantFor.get(Calendar.YEAR) == calendarDateToCopy.get(Calendar.YEAR))) {
				throw new InvalidAttributeValueException("copyPreviousBudgetToSelectedMonth", "dateMeantFor",
						dateMeantForAsDate);
			}

			userBudgetList = getUserBudgetRepository().fetchAllUserBudget(financialPortfolioId, dateMeantForAsDate);

			if (CollectionUtils.isEmpty(userBudgetList)) {
				userBudgetList = getUserBudgetRepository().fetchAllUserBudget(financialPortfolioId, dateToCopyAsDate);

				if (CollectionUtils.isNotEmpty(userBudgetList)) {
					List<UserBudget> newUserBudgetList = new ArrayList<UserBudget>();
					for (UserBudget userBudget : userBudgetList) {
						// Create budget otherwise
						UserBudget newUserBudget = new UserBudget();
						Long budgetIdL = Long.parseLong(userBudget.getFinancialPortfolioId())
								+ Integer.parseInt(dateMeantFor) + userBudget.getCategoryId();
						newUserBudget.setBudgetId(new BigInteger(budgetIdL.toString()));
						newUserBudget.setPlanned(userBudget.getPlanned());
						newUserBudget.setAutoGeneratedBudget(userBudget.getAutoGeneratedBudget());
						newUserBudget.setCategoryId(userBudget.getCategoryId());
						newUserBudget.setDateMeantFor(dateMeantForAsDate);
						newUserBudget.setFinancialPortfolioId(userBudget.getFinancialPortfolioId());
						newUserBudgetList.add(newUserBudget);

					}

					copiedUserBudgets = getUserBudgetRepository().saveAll(newUserBudgetList);
				}
			} else {
				throw new ResourceAlreadyPresentException("UserBudget", "dateMeantFor", dateMeantFor);
			}

		} catch (ParseException e) {
			logger.error(e + " Unable to add date to the user budget");
		}
		return copiedUserBudgets;
	}

	/**
	 * Fetch all dates with user budget for the user
	 */
	@Override
	public Set<Integer> fetchAllDatesWithUserBudget(String financialPortfolioId) {

		List<Date> dateMeantFor = getUserBudgetRepository().findAllDatesWithDateById(financialPortfolioId);
		Set<Date> setOfDateMeantFor = new HashSet<Date>(dateMeantFor);
		Set<Integer> setOfDatesAsInteger = new HashSet<Integer>();

		// return null if there is no data
		if (CollectionUtils.isEmpty(dateMeantFor)) {
			return null;
		} else {
			DateFormat dateFormat = new SimpleDateFormat(GenericConstants.DATE_FORMAT_FRONTEND);
			setOfDateMeantFor.forEach(date -> setOfDatesAsInteger.add(Integer.parseInt(dateFormat.format(date))));

		}

		return setOfDatesAsInteger;
	}

	/**
	 * Change the category of the user budget
	 */
	@Override
	@CacheEvict(key = "{#financialPortfolioId, #formData.get(\"dateMeantFor\").get(0)}")
	public UserBudget changeCategoryWithUserBudget(String financialPortfolioId,
			MultiValueMap<String, String> formData) {

		UserBudget savedUserBudget = new UserBudget();
		String dateMeantFor = formData.get(DashboardConstants.Budget.DATE_MEANT_FOR).get(0);
		String categoryId = formData.get(DashboardConstants.Budget.CATEGORY_ID).get(0);
		String newCategoryId = formData.get(DashboardConstants.Budget.NEW_CATEGORY_ID).get(0);
		DateFormat format = new SimpleDateFormat(DashboardConstants.DATE_FORMAT, Locale.ENGLISH);

		if (ERStringUtils.isBlank(categoryId) && ERStringUtils.isBlank(newCategoryId)) {
			throw new InvalidAttributeValueException("changeCategoryWithUserBudget", "category_id", categoryId);
		}

		try {
			Date dateMeantForAsDate = format.parse(dateMeantFor);
			List<Integer> categoryIds = new ArrayList<Integer>();
			categoryIds.add(Integer.parseInt(categoryId));
			List<UserBudget> categoryBudget = getUserBudgetRepository().fetchUserBudgetWithCategoryIds(categoryIds,
					financialPortfolioId, dateMeantForAsDate);

			if (CollectionUtils.isNotEmpty(categoryBudget)) {
				List<UserBudget> newUserBudgetList = categoryBudget.stream().map(userBudget -> {
					userBudget.setCategoryId(Integer.parseInt(newCategoryId));
					return userBudget;
				}).collect(Collectors.toList());

				List<UserBudget> savedUserBudgetList = getUserBudgetRepository().saveAll(newUserBudgetList);
				if (CollectionUtils.isNotEmpty(savedUserBudgetList)) {
					savedUserBudget = savedUserBudgetList.get(0);
				}
			}

		} catch (ParseException e) {
			logger.error(e + "changeCategoryWithUserBudget: Unable to add date to the user budget");
		}

		return savedUserBudget;
	}

	/**
	 * Delete all user budgets for user
	 */
	@Override
	public void deleteAllUserBudgets(String financialPortfolioId) {
		getUserBudgetRepository().deleteAllUserBudgets(financialPortfolioId);
	}

	public UserBudgetRepository getUserBudgetRepository() {
		return userBudgetRepository;
	}

	public IUserTransactionService getUserTransactionService() {
		return userTransactionService;
	}

}
