package com.example.javadoc.module.catalog.service;

import com.example.javadoc.infrastructure.storage.ImageStorage;
import com.example.javadoc.module.catalog.dto.request.DeleteFoodRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateFoodRequest;
import com.example.javadoc.module.catalog.entity.Category;
import com.example.javadoc.module.catalog.mapper.CategoryMapper;
import com.example.javadoc.module.catalog.mapper.FoodMapper;
import com.example.javadoc.module.catalog.repository.CategoryRepository;
import com.example.javadoc.module.catalog.repository.FoodRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodServiceConsistencyTests {

    @Test
    void movesFoodInsideOneTransactionAfterBothCategoriesHaveBeenValidated() {
        CategoryRepository categories = mock(CategoryRepository.class);
        FoodRepository foods = mock(FoodRepository.class);
        ImageStorage images = mock(ImageStorage.class);
        PlatformTransactionManager transactions = transactionManager();
        FoodService service = service(categories, foods, images, transactions);

        Category source = category("source", food("food", "/old.png"));
        Category target = category("target");
        when(categories.findById("source")).thenReturn(Optional.of(source));
        when(categories.findById("target")).thenReturn(Optional.of(target));
        when(foods.appendFoods(eq("target"), anyList())).thenReturn(updated(1));
        when(foods.deleteFood("source", "food")).thenReturn(updated(1));

        service.updateFoodDetails(new UpdateFoodRequest(
                "source", "target", "food", "新菜名", null, null, null, null));

        var order = inOrder(foods);
        order.verify(foods).appendFoods(eq("target"), anyList());
        order.verify(foods).deleteFood("source", "food");
        verify(transactions).commit(any(TransactionStatus.class));
    }

    @Test
    void doesNotWriteWhenTheTargetCategoryIsMissingAndRollsBackTheTransaction() {
        CategoryRepository categories = mock(CategoryRepository.class);
        FoodRepository foods = mock(FoodRepository.class);
        PlatformTransactionManager transactions = transactionManager();
        FoodService service = service(categories, foods, mock(ImageStorage.class), transactions);

        when(categories.findById("source")).thenReturn(Optional.of(category("source", food("food", "/old.png"))));
        when(categories.findById("target")).thenReturn(Optional.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> service.updateFoodDetails(
                new UpdateFoodRequest("source", "target", "food", null, null, null, null, null)))
                .withMessage("目标分类不存在");

        verify(foods, never()).appendFoods(any(), anyList());
        verify(foods, never()).deleteFood(any(), any());
        verify(transactions).rollback(any(TransactionStatus.class));
    }

    @Test
    void doesNotReportSuccessWhenAFoodUpdateMatchesNothing() {
        FoodRepository foods = mock(FoodRepository.class);
        FoodService service = service(mock(CategoryRepository.class), foods, mock(ImageStorage.class), transactionManager());
        when(foods.updateFoodDetails("category", "food", "菜名", null, null, null)).thenReturn(updated(0));

        assertThatIllegalArgumentException().isThrownBy(() -> service.updateFoodDetails(
                new UpdateFoodRequest("category", "category", "food", "菜名", null, null, null, null)))
                .withMessage("菜品或分类不存在");
    }

    @Test
    void deletesTheStoredImageOnlyAfterTheFoodDocumentWasDeleted() {
        CategoryRepository categories = mock(CategoryRepository.class);
        FoodRepository foods = mock(FoodRepository.class);
        ImageStorage images = mock(ImageStorage.class);
        FoodService service = service(categories, foods, images, transactionManager());
        when(foods.removeFood("category", "food")).thenReturn(category("category", food("food", "/stored.png")));

        service.deleteFood(new DeleteFoodRequest("category", "food", "/client-supplied.png"));

        var order = inOrder(foods, images);
        order.verify(foods).removeFood("category", "food");
        order.verify(images).delete("/stored.png");
    }

    @Test
    void usesTheStoredImageInsteadOfTheClientSuppliedOldImageWhenReplacingAnImage() {
        CategoryRepository categories = mock(CategoryRepository.class);
        FoodRepository foods = mock(FoodRepository.class);
        ImageStorage images = mock(ImageStorage.class);
        FoodService service = service(categories, foods, images, transactionManager());
        when(categories.findById("category")).thenReturn(Optional.of(category("category", food("food", "/stored.png"))));
        when(foods.updateFoodDetails("category", "food", null, null, null, "/new.png"))
                .thenReturn(updated(1));

        service.updateFood(new UpdateFoodRequest(
                "category", "category", "food", null, null, null, "/new.png", "/client-supplied.png"));

        verify(images).delete("/stored.png");
        verify(images, never()).delete("/client-supplied.png");
    }

    private FoodService service(CategoryRepository categories, FoodRepository foods,
                                ImageStorage images, PlatformTransactionManager transactions) {
        return new FoodService(categories, foods, mock(CategoryMapper.class), mock(FoodMapper.class), images,
                new TransactionTemplate(transactions));
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        return manager;
    }

    private UpdateResult updated(long matchedCount) {
        return UpdateResult.acknowledged(matchedCount, matchedCount, null);
    }

    private Category category(String id, Category.FoodItem... foods) {
        Category category = new Category();
        category.setId(id);
        category.setFoods(List.of(foods));
        return category;
    }

    private Category.FoodItem food(String id, String imageUrl) {
        Category.FoodItem food = new Category.FoodItem();
        food.setId(id);
        food.setImageUrl(imageUrl);
        return food;
    }
}
