package com.example.demo1.module.market.controller;

import com.example.demo1.module.market.dto.*;
import com.example.demo1.module.market.service.FoodService;
import com.example.demo1.module.market.service.MarketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;
    private final FoodService foodService;

    @PostMapping("/addCategory")
    public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
        return marketService.addCategory(dto.getName(), dto.getImage());
    }

    @DeleteMapping("/deleteCategory")
    public Object deleteCategory(@RequestParam("id") String id) {
        return marketService.deleteCategory(id);
    }

    @PutMapping("/updateCategory")
    public Object updateCategory(@Valid @RequestBody UpdateCategoryDTO dto) {
        return marketService.updateCategory(dto.getId(), dto.getName(), dto.getImage());
    }

    @PutMapping("/addFood")
    public Object addFood(@Valid @RequestBody AddFoodDTO dto) {
        return foodService.addFood(dto);
    }

    @DeleteMapping("/deleteFood")
    public Object deleteFood(@Valid @RequestBody DeleteFoodDTO dto) {
        return foodService.deleteFood(dto);
    }

    @PutMapping("/updateFoodWithNum")
    public Object updateFoodWithNum(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> foodIds = (List<String>) body.get("foodIds");
        int num = body.containsKey("increment") ? ((Number) body.get("increment")).intValue() : 1;
        return foodService.updateFoodWithNum(foodIds, num);
    }

    @PutMapping("/updateFoodWithoutImage")
    public Object updateFoodWithoutImage(@Valid @RequestBody UpdateFoodDTO dto) {
        return foodService.updateFoodWithoutImage(dto);
    }

    @PutMapping("/updateFood")
    public Object updateFood(@Valid @RequestBody UpdateFoodDTO dto) {
        return foodService.updateFood(dto);
    }

    @GetMapping("/getAll")
    public Object getAll() {
        return marketService.getAll();
    }

    @GetMapping("/findFoods")
    public Object findFoods(@RequestParam("text") String text) {
        return marketService.findFoods(text);
    }

    @PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
        try {


            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = System.currentTimeMillis() + ext;

            // 使用绝对路径
            String projectDir = System.getProperty("user.dir");
            File destDir = new File(projectDir, "static/images/market");
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File dest = new File(destDir, filename);
            file.transferTo(dest);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", "/static/images/market/" + filename);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }
}