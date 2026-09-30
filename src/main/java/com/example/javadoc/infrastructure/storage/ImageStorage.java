package com.example.javadoc.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

/** 图片存储边界；不依赖 HTTP 上传类型。输入流由调用方关闭。 */
public interface ImageStorage {

    String store(InputStream content, String originalFilename) throws IOException;

    void delete(String imageUrl);
}
