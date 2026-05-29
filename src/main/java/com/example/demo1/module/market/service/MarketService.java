package com.example.demo1.module.market.service;

import com.example.demo1.module.market.entity.Market;
import com.example.demo1.module.market.entity.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final MongoTemplate mongoTemplate;

    public Market addCategory(String name, String image) {
        if (marketRepository.findAll().stream().anyMatch(m -> m.getName().equals(name))) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        Market market = new Market();
        market.setName(name);
        market.setImage(image);
        return marketRepository.save(market);
    }

    public Map<String, Object> deleteCategory(String id) {
        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));

        deleteImageFile(market.getImage());
        marketRepository.deleteById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", "删除成功");
        return result;
    }

    public Market updateCategory(String id, String name, String image) {
        boolean duplicate = marketRepository.findAll().stream()
                .anyMatch(m -> m.getName().equals(name) && !m.getId().equals(id));
        if (duplicate) {
            throw new IllegalArgumentException("分类名称已存在");
        }

        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        market.setName(name);
        market.setImage(image);
        return marketRepository.save(market);
    }

    public List<Market> getAll() {
        return marketRepository.findAll();
    }

    public List<Market> findFoods(String text) {
        Query query = Query.query(Criteria.where("foods.burden")
                .regex(".*" + text + ".*", "i"));
        return mongoTemplate.find(query, Market.class);
    }

    public void deleteImageFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        try {
            String filename = Paths.get(imageUrl).getFileName().toString();
            Path filePath = Paths.get("static/images/market", filename);
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }
}