package in.co.everyrupee.controller.income;

import in.co.everyrupee.pojo.income.Category;
import in.co.everyrupee.service.income.ICategoryService;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage API Category
 *
 * @author Nagarjun Nagesh
 */
@RestController
@RequestMapping("/api/category")
public class CategoryController {

  @Autowired private ICategoryService categoryService;

  // Get a Single User Transaction
  @RequestMapping(value = "/", method = RequestMethod.GET)
  public List<Category> getCategoryById(Principal userPrincipal) {

    return categoryService.fetchCategories();
  }
}
