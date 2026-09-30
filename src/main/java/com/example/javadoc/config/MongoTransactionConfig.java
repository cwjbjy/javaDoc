package com.example.javadoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MongoDB 多文档写入事务。
 *
 * <p>跨分类移动菜品及批量修改被点次数依赖此事务。MongoDB 服务器必须以副本集或
 * 分片集群运行；独立实例不支持多文档事务。</p>
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    public TransactionTemplate mongoTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new TransactionTemplate(mongoTransactionManager);
    }
}
