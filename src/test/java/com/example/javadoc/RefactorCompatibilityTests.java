package com.example.javadoc;

import com.example.javadoc.module.catalog.dto.request.AddFoodsRequest;
import com.example.javadoc.module.catalog.dto.request.CreateCategoryRequest;
import com.example.javadoc.module.catalog.dto.request.DeleteFoodRequest;
import com.example.javadoc.module.catalog.dto.request.FoodItemRequest;
import com.example.javadoc.module.catalog.dto.request.UpdateFoodRequest;
import com.example.javadoc.module.catalog.entity.Category;
import com.example.javadoc.module.catalog.mapper.CategoryMapper;
import com.example.javadoc.module.catalog.mapper.FoodMapper;
import com.example.javadoc.module.catalog.repository.FoodRepository;
import com.example.javadoc.module.order.dto.request.CreateOrderRequest;
import com.example.javadoc.module.order.dto.response.OrderListResponse;
import com.example.javadoc.module.order.entity.Order;
import com.example.javadoc.module.order.mapper.OrderMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证内部重命名不会改变旧客户端或历史 MongoDB 文档的契约；不执行数据库读写。 */
@SpringBootTest
class RefactorCompatibilityTests {

    @Autowired ObjectMapper json;
    @Autowired MappingMongoConverter mongo;
    @Autowired CategoryMapper categoryMapper;
    @Autowired FoodMapper foodMapper;
    @Autowired OrderMapper orderMapper;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping routes;

    @Test
    void preservesAllFourteenLegacyRoutes() {
        Set<String> actual = routes.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName()
                        .startsWith("com.example.javadoc.module."))
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                        .flatMap(method -> entry.getKey().getPatternValues().stream()
                                .map(path -> method.name() + " " + path)))
                .collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrder(
                "POST /api/market/addCategory", "DELETE /api/market/deleteCategory",
                "PUT /api/market/updateCategory", "PUT /api/market/addFood",
                "DELETE /api/market/deleteFood", "PUT /api/market/updateFoodWithNum",
                "PUT /api/market/updateFoodWithoutImage", "PUT /api/market/updateFood",
                "GET /api/market/getAll", "GET /api/market/findFoods",
                "POST /api/market/uploadImage", "POST /api/order/addOrder",
                "GET /api/order/getOrder", "DELETE /api/order/deleteOrder");
    }

    @Test
    void preservesCategoryAndFoodJsonFieldsThroughMappers() {
        var category = categoryMapper.toEntity(json.readValue(
                """
                {"name":"热菜","image":"/static/images/market/category.png"}
                """, CreateCategoryRequest.class));
        var request = json.readValue(
                """
                {"categoryId":"category-1","foods":[
                  {"name":"鸡丁","describe":"描述","burden":"鸡肉","image":"/food.png","num":12}
                ]}
                """, AddFoodsRequest.class);
        var food = foodMapper.toEntity(request.foods().get(0));
        assertThat(food.getDescription()).isEqualTo("描述");
        assertThat(food.getIngredients()).isEqualTo("鸡肉");
        assertThat(food.getOrderCount()).isEqualTo(12);
        food.setId("food-1");
        category.setId("category-1");
        category.getFoods().add(food);

        assertThat(json.readTree(json.writeValueAsString(categoryMapper.toResponse(category))))
                .isEqualTo(json.readTree("""
                {"id":"category-1","name":"热菜","image":"/static/images/market/category.png","foods":[
                  {"id":"food-1","name":"鸡丁","describe":"描述","burden":"鸡肉","image":"/food.png","num":12}
                ]}
                """));
    }

    @Test
    void defaultsMissingOrderCountToZero() {
        var request = json.readValue(
                """
                {"name":"鸡丁","describe":"描述","burden":"鸡肉","image":"/food.png"}
                """, FoodItemRequest.class);
        assertThat(foodMapper.toEntity(request).getOrderCount()).isZero();
    }

    @Test
    void preservesImageFieldsInUpdateAndDeleteRequests() {
        var update = json.readValue("""
                {"categoryId":"a","targetCategoryId":"b","foodId":"f","describe":"描述",
                 "burden":"鸡肉","image":"/new.png","oldImage":"/old.png"}
                """, UpdateFoodRequest.class);
        assertThat(update.description()).isEqualTo("描述");
        assertThat(update.ingredients()).isEqualTo("鸡肉");
        assertThat(update.imageUrl()).isEqualTo("/new.png");
        assertThat(update.oldImageUrl()).isEqualTo("/old.png");
        var delete = json.readValue("""
                {"categoryId":"a","foodId":"f","image":"/old.png"}
                """, DeleteFoodRequest.class);
        assertThat(delete.imageUrl()).isEqualTo("/old.png");
    }

    @Test
    void preservesOrderRequestAndResponseFields() {
        var request = json.readValue("""
                {"date":"2026-09-30","num":2,"foods":[
                  {"_id":"food-1","name":"鸡丁","describe":"描述","burden":"鸡肉",
                   "image":"/food.png","value":2}
                ]}
                """, CreateOrderRequest.class);
        var order = orderMapper.toEntity(request);
        assertThat(order.getTotalQuantity()).isEqualTo(2);
        assertThat(order.getFoods().get(0).getQuantity()).isEqualTo(2);
        assertThat(order.getFoods().get(0).getId()).isEqualTo("food-1");
        order.setId("order-1");
        // 固定时间输出以便对完整响应字段进行比对。
        order.setCreatedAt(null);
        var response = new OrderListResponse(List.of(orderMapper.toResponse(order)), 1);
        assertThat(json.readTree(json.writeValueAsString(response))).isEqualTo(json.readTree("""
                {"foods":[{"id":"order-1","date":"2026-09-30","createdAt":null,"num":2,"foods":[
                  {"id":"food-1","name":"鸡丁","describe":"描述","burden":"鸡肉",
                   "image":"/food.png","value":2}
                ]}],"total":1}
                """));
    }

    @Test
    void readsAndWritesLegacyCategoryDocumentsAndTypeAlias() {
        Document legacy = new Document("_id", new ObjectId())
                .append("name", "热菜").append("image", "/category.png")
                .append("_class", "com.example.javadoc.module.market.entity.Market")
                .append("foods", List.of(new Document("_id", "food-1").append("name", "鸡丁")
                        .append("describe", "描述").append("burden", "鸡肉")
                        .append("image", "/food.png").append("num", 12)));
        Category category = mongo.read(Category.class, legacy);
        assertThat(category.getFoods().get(0).getOrderCount()).isEqualTo(12);
        Document written = new Document();
        mongo.write(category, written);
        assertThat(written).isEqualTo(legacy);
        assertThat(mongo.read(Object.class, legacy)).isInstanceOf(Category.class);
        assertThat(mongo.getMappingContext().getRequiredPersistentEntity(Category.class).getCollection())
                .isEqualTo("markets");
    }

    @Test
    void readsAndWritesLegacyOrderDocuments() {
        Document legacy = new Document("_id", new ObjectId())
                .append("date", "2026-09-30").append("num", 2)
                .append("_class", "com.example.javadoc.module.order.entity.Order")
                .append("foods", List.of(new Document("_id", "food-1").append("name", "鸡丁")
                        .append("describe", "描述").append("burden", "鸡肉")
                        .append("image", "/food.png").append("value", 2)));
        Order order = mongo.read(Order.class, legacy);
        assertThat(order.getTotalQuantity()).isEqualTo(2);
        assertThat(order.getFoods().get(0).getQuantity()).isEqualTo(2);
        Document written = new Document();
        mongo.write(order, written);
        assertThat(written).isEqualTo(legacy);
    }

    @Test
    void mapsRepositoryUpdatesToLegacyMongoFields() {
        MongoTemplate template = mock(MongoTemplate.class);
        FoodRepository repository = new FoodRepository(template);
        repository.updateFoodDetails("category-1", "food-1", "鸡丁", "描述", "鸡肉", "/food.png");
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(template).updateFirst(query.capture(), update.capture(), eq(Category.class));
        var entity = mongo.getMappingContext().getRequiredPersistentEntity(Category.class);
        assertThat(new QueryMapper(mongo).getMappedObject(query.getValue().getQueryObject(), entity))
                .isEqualTo(new Document("_id", "category-1").append("foods._id", "food-1"));
        assertThat(new UpdateMapper(mongo).getMappedObject(update.getValue().getUpdateObject(), entity))
                .isEqualTo(new Document("$set", new Document("foods.$.name", "鸡丁")
                        .append("foods.$.describe", "描述").append("foods.$.burden", "鸡肉")
                        .append("foods.$.image", "/food.png")));
    }

    @Test
    void mapsOrderCountAndEmbeddedDeletionToLegacyMongoFields() {
        MongoTemplate template = mock(MongoTemplate.class);
        FoodRepository repository = new FoodRepository(template);
        repository.incrementFoodOrderCount("food-1", 2);
        var increment = ArgumentCaptor.forClass(UpdateDefinition.class);
        var query = ArgumentCaptor.forClass(Query.class);
        verify(template).updateMulti(query.capture(), increment.capture(), eq(Category.class));
        var entity = mongo.getMappingContext().getRequiredPersistentEntity(Category.class);
        assertThat(new QueryMapper(mongo).getMappedObject(query.getValue().getQueryObject(), entity))
                .isEqualTo(new Document("foods._id", "food-1"));
        assertThat(new UpdateMapper(mongo).getMappedObject(increment.getValue().getUpdateObject(), entity))
                .isEqualTo(new Document("$inc", new Document("foods.$.num", 2)));

        repository.deleteFood("category-1", "food-1");
        var deletion = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(template).updateFirst(query.capture(), deletion.capture(), eq(Category.class));
        assertThat(new UpdateMapper(mongo).getMappedObject(deletion.getValue().getUpdateObject(), entity))
                .isEqualTo(new Document("$pull", new Document("foods", new Document("_id", "food-1"))));
    }
}
