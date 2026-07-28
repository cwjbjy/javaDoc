package com.example.demo1.module.market.service;

import com.example.demo1.module.market.dto.request.CreateCategoryDTO;
import com.example.demo1.module.market.dto.response.CategoryResponse;
import com.example.demo1.module.market.entity.Market;
import com.example.demo1.module.market.entity.MarketRepository;
import com.example.demo1.module.market.mapper.MarketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final MarketMapper marketMapper;
    private final MongoTemplate mongoTemplate;

    public CategoryResponse addCategory(CreateCategoryDTO dto) {
        if (marketRepository.findByName(dto.name()).isPresent()) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        Market saved = marketRepository.save(marketMapper.toEntity(dto));
        return marketMapper.toResponse(saved);
    }

    public String deleteCategory(String id) {
        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));

        deleteImageFile(market.getImage());
        marketRepository.deleteById(id);
        return "删除成功";
    }

    public CategoryResponse updateCategory(String id, String name, String image) {
        boolean duplicate = marketRepository.existsByNameAndIdNot(name, id);
        if (duplicate) {
            throw new IllegalArgumentException("分类名称已存在");
        }

        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        market.setName(name);
        market.setImage(image);
        Market saved = marketRepository.save(market);
        return marketMapper.toResponse(saved);
    }

    public List<CategoryResponse> getAll() {
        List<Market> markets = marketRepository.findAll();
        return marketMapper.toResponseList(markets);
    }

    public List<CategoryResponse> findFoods(String text) {
        Query query = Query.query(Criteria.where("foods.burden")
                .regex(".*" + text + ".*", "i"));
        List<Market> markets = mongoTemplate.find(query, Market.class);
        return marketMapper.toResponseList(markets);
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