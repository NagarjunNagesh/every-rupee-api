package in.co.everyrupee.service.income;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.MultiValueMap;

import in.co.everyrupee.pojo.income.UserBudget;

public interface IUserBudgetService {

	UserBudget saveAutoGeneratedUserBudget(MultiValueMap<String, String> formData, String pFinancialPortfolioId);

	List<UserBudget> updateAutoGeneratedBudget(MultiValueMap<String, String> formData, String formFieldName,
			String financialPortfolioId, String dateMeantFor);

	void deleteAllUserBudgets(String financialPortfolioId, String dateMeantFor, Boolean autoGenerated);

	void deleteAutoGeneratedUserBudgets(String categoryIds, String financialPortfolioId, String dateMeantFor);

	List<UserBudget> fetchAllUserBudget(String pFinancialPortfolioId, String dateMeantFor);

	List<UserBudget> fetchAutoGeneratedUserBudgetByCategoryIds(String categoryIds, String pFinancialPortfolioId,
			String dateMeantFor);

	void updateAutoGeneratedUserBudget(String financialPortfolioId, Map<Integer, Double> categoryIdAndCategoryTotal,
			String dateMeantFor);

	void deleteUserBudgets(String categoryIds, String financialPortfolioId, String dateMeantFor);

	List<UserBudget> copyPreviousBudgetToSelectedMonth(String financialPortfolioId,
			MultiValueMap<String, String> formData);

	Set<Integer> fetchAllDatesWithUserBudget(String financialPortfolioId);

	UserBudget changeCategoryWithUserBudget(String financialPortfolioId, MultiValueMap<String, String> formData);

	void deleteAllUserBudgets(String financialPortfolioId);

	void copyFromPreviousMonth();

}
