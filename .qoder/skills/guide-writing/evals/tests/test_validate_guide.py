from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = ROOT / "scripts" / "validate_guide.py"


class ValidateGuideCliTests(unittest.TestCase):
    def run_validator(self, markdown: str) -> tuple[subprocess.CompletedProcess[str], dict]:
        with tempfile.TemporaryDirectory() as directory:
            guide = Path(directory) / "guide.md"
            guide.write_text(markdown, encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(VALIDATOR), str(guide), "--format", "json"],
                capture_output=True,
                text=True,
                encoding="utf-8",
                check=False,
            )
        payload = json.loads(result.stdout) if result.stdout.strip() else {}
        return result, payload

    def test_short_guide_without_toc_passes(self) -> None:
        markdown = """# OAuth PKCE 入门

## 为什么需要 PKCE

授权码可能被截获。

## 基本流程

客户端使用一次性校验信息保护授权码交换。
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(payload["valid"])
        self.assertEqual(payload["errors"], [])

    def test_four_content_sections_require_toc(self) -> None:
        markdown = """# Redis 数据结构

## String
内容。
## Hash
内容。
## List
内容。
## Set
内容。
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 1)
        self.assertIn("toc-required", {error["code"] for error in payload["errors"]})

    def test_broken_toc_anchor_fails(self) -> None:
        markdown = """# Redis 数据结构

## 目录

1. [String](#string)
2. [Hash](#missing-hash)
3. [List](#list)
4. [Set](#set)

## String
内容。
## Hash
内容。
## List
内容。
## Set
内容。
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 1)
        self.assertIn("toc-target-missing", {error["code"] for error in payload["errors"]})

    def test_unbalanced_code_fence_fails(self) -> None:
        markdown = """# 示例

## 片段

```python
print("hello")
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 1)
        self.assertIn("unbalanced-code-fence", {error["code"] for error in payload["errors"]})

    def test_template_placeholders_fail(self) -> None:
        markdown = """# [主题] 指南

## 概览

TBD
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 1)
        self.assertIn("template-placeholder", {error["code"] for error in payload["errors"]})

    def test_multiple_h1_headings_fail(self) -> None:
        markdown = """# 第一标题

# 第二标题
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 1)
        self.assertIn("h1-count", {error["code"] for error in payload["errors"]})

    def test_unicode_toc_targets_pass(self) -> None:
        markdown = """# CAP 定理

## 目录

1. [问题背景](#问题背景)
2. [网络分区](#网络分区)
3. [一致性](#一致性)
4. [可用性](#可用性)

## 问题背景
内容。
## 网络分区
内容。
## 一致性
内容。
## 可用性
内容。
"""
        result, payload = self.run_validator(markdown)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(payload["valid"])


if __name__ == "__main__":
    unittest.main()
