package in.co.everyrupee.events.income;

import java.util.Map;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Save Budget based on user transaction
 *
 * @author Nagarjun
 */
public class OnFetchCategoryTotalCompleteEvent extends ApplicationEvent {

  private static final long serialVersionUID = 5291464855131518156L;
  private final Map<Integer, Double> categoryIdAndTotalAmount;
  private final String dateMeantFor;
  private final String financialPortfolioId;

  public OnFetchCategoryTotalCompleteEvent(
      final Map<Integer, Double> categoryIdAndTotalAmount,
      final String dateMeantFor,
      final String financialPortfolioId) {
    super(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    this.categoryIdAndTotalAmount = categoryIdAndTotalAmount;
    this.dateMeantFor = dateMeantFor;
    this.financialPortfolioId = financialPortfolioId;
  }

  public Map<Integer, Double> getCategoryIdAndTotalAmount() {
    return categoryIdAndTotalAmount;
  }

  public String getDateMeantFor() {
    return dateMeantFor;
  }

  public String getFinancialPortfolioId() {
    return financialPortfolioId;
  }
}
