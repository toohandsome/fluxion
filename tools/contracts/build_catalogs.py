from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Dict, List


REPO_ROOT = Path(__file__).resolve().parents[2]
DOCS_ROOT = REPO_ROOT / "docs" / "phase-1"
GENERATED_DIR = REPO_ROOT / "generated"


DOC_REF_MAP: Dict[str, List[str]] = {
    "CYCLIC_GRAPH": [
        "docs/phase-1/runtime-semantics.md#3.2-连通性约束",
        "docs/phase-1/model-json-contract.md#5.1-结构规则",
    ],
    "NO_START_NODE": [
        "docs/phase-1/runtime-semantics.md#3.1-起始节点与结束节点",
    ],
    "NO_TERMINAL_NODE": [
        "docs/phase-1/runtime-semantics.md#3.1-起始节点与结束节点",
    ],
    "UNREACHABLE_NODE": [
        "docs/phase-1/runtime-semantics.md#3.2-连通性约束",
        "docs/phase-1/model-json-contract.md#8-校验阶段与错误归类",
    ],
    "DISCONNECTED_SUBGRAPH": [
        "docs/phase-1/runtime-semantics.md#3.2-连通性约束",
        "docs/phase-1/model-json-contract.md#5.1-结构规则",
    ],
    "NODE_NOT_ON_START_TO_END_PATH": [
        "docs/phase-1/runtime-semantics.md#3.2-连通性约束",
        "docs/phase-1/model-json-contract.md#5.1-结构规则",
    ],
    "INVALID_CONDITION_BRANCH": [
        "docs/phase-1/runtime-semantics.md#3.3-条件节点约束",
        "docs/phase-1/model-json-contract.md#5.3-分支规则",
    ],
    "FLOW_OUTPUT_MAPPING_MISSING": [
        "docs/phase-1/runtime-semantics.md#4.5-流程最终输出",
        "docs/phase-1/error-codes.md#7.1-模型编译与结构校验",
    ],
    "NODE_SCHEMA_INVALID": [
        "docs/phase-1/model-json-contract.md#4-第一层json-schema-负责什么",
        "docs/phase-1/node-schemas.md",
    ],
}


FIX_HINT_MAP: Dict[str, str] = {
    "CYCLIC_GRAPH": "检查 edges，移除回边或自循环，确保主流程是 DAG。",
    "NO_START_NODE": "确保图中至少存在一个入度为 0 的节点。",
    "NO_TERMINAL_NODE": "确保图中至少存在一个出度为 0 的节点。",
    "UNREACHABLE_NODE": "检查 edges，确保所有节点都能从至少一个自动推导出的起始节点到达。",
    "DISCONNECTED_SUBGRAPH": "检查孤岛节点或独立子图，确保忽略方向后所有节点属于同一连通分量。",
    "NODE_NOT_ON_START_TO_END_PATH": "检查节点是否能到达至少一个终止节点，避免中途悬空路径。",
    "INVALID_CONDITION_BRANCH": "condition 节点出边只能使用 true/false；非 condition 节点只能使用 default。",
    "FLOW_OUTPUT_MAPPING_MISSING": "发布前补齐 flowOutputMapping，并确保其能基于稳定命名空间求值。",
    "NODE_SCHEMA_INVALID": "回看 node-schemas 与 model-json schema，补齐缺失字段并修正配置结构。",
    "DOC_CONTRACT_DRIFT": "同步检查 contract 文档入口、technical-solution 索引边界与对应 fixture / harness，避免出现第二套事实源。",
}


RUNTIME_RULES = [
    {
        "ruleId": "runtime.start-terminal",
        "title": "起始节点与结束节点",
        "summary": "入度为 0 的节点是 start，出度为 0 的节点是 terminal。",
        "docRefs": ["docs/phase-1/runtime-semantics.md#3.1-起始节点与结束节点"],
    },
    {
        "ruleId": "runtime.connectivity",
        "title": "连通性约束",
        "summary": "所有节点都必须可从 start 到达、能到达 terminal，且属于同一连通分量。",
        "docRefs": ["docs/phase-1/runtime-semantics.md#3.2-连通性约束"],
    },
    {
        "ruleId": "runtime.condition-branch",
        "title": "条件分支约束",
        "summary": "condition 节点只允许 true/false 两个分支键。",
        "docRefs": ["docs/phase-1/runtime-semantics.md#3.3-条件节点约束"],
    },
    {
        "ruleId": "runtime.flow-output",
        "title": "流程最终输出",
        "summary": "发布后的运行时模型必须包含 flowOutputMapping。",
        "docRefs": ["docs/phase-1/runtime-semantics.md#4.5-流程最终输出"],
    },
]

SUPPLEMENTAL_ERROR_CODES = [
    {
        "errorCode": "DOC_CONTRACT_DRIFT",
        "layer": "HARNESS",
        "description": "contract 文档入口、摘要文档与 harness/fixture 出现漂移",
        "docRefs": [
            "docs/doc-structure.md",
            "docs/phase-1/admin-api-contract.md",
            "docs/phase-1/technical-solution.md",
            "docs/phase-1/schedule-contract.md",
            "docs/schema-pg.sql",
        ],
        "fixHint": FIX_HINT_MAP["DOC_CONTRACT_DRIFT"],
        "ownerModule": "fluxion-common",
        "sourcePath": "docs/phase-1/admin-api-contract.md",
        "lineHint": "section-10-doc-sync",
    }
]

LINE_HINT_MAP = {
    "CYCLIC_GRAPH": "section-3.2-connectivity",
    "NO_START_NODE": "section-3.1-start-terminal",
    "NO_TERMINAL_NODE": "section-3.1-start-terminal",
    "UNREACHABLE_NODE": "section-3.2-connectivity",
    "DISCONNECTED_SUBGRAPH": "section-3.2-connectivity",
    "NODE_NOT_ON_START_TO_END_PATH": "section-3.2-connectivity",
    "INVALID_CONDITION_BRANCH": "section-3.3-condition-branch",
    "FLOW_OUTPUT_MAPPING_MISSING": "section-4.5-flow-output",
    "NODE_SCHEMA_INVALID": "section-4-schema-contract",
}


DIAGNOSTIC_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "https://fluxion.dev/schema/diagnostic-schema.json",
    "title": "Fluxion Harness Diagnostics",
    "type": "object",
    "additionalProperties": False,
    "required": ["version", "diagnostics"],
    "properties": {
        "version": {"type": "string", "const": "1.0"},
        "diagnostics": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "tool",
                    "suite",
                    "caseId",
                    "stage",
                    "severity",
                    "errorCode",
                    "message",
                    "ownerModule",
                    "fixHint",
                    "docRefs",
                    "suggestedCommand",
                    "candidateFiles",
                    "relatedFixture",
                    "sourcePath",
                    "lineHint",
                ],
                "properties": {
                    "tool": {"type": "string"},
                    "suite": {"type": "string"},
                    "caseId": {"type": "string"},
                    "stage": {"type": "string"},
                    "severity": {"type": "string", "enum": ["ERROR", "WARNING", "INFO"]},
                    "errorCode": {"type": "string"},
                    "message": {"type": "string"},
                    "ownerModule": {"type": "string"},
                    "module": {"type": ["string", "null"]},
                    "field": {"type": ["string", "null"]},
                    "nodeId": {"type": ["string", "null"]},
                    "attemptSummary": {
                        "type": ["array", "null"],
                        "items": {
                            "type": "object"
                        }
                    },
                    "fixHint": {"type": "string"},
                    "suggestedCommand": {"type": "string"},
                    "candidateFiles": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                    "relatedFixture": {"type": ["string", "null"]},
                    "sourcePath": {"type": ["string", "null"]},
                    "lineHint": {"type": ["string", "null"]},
                    "docRefs": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                },
            },
        },
    },
}


def parse_error_codes() -> List[dict]:
    path = DOCS_ROOT / "error-codes.md"
    lines = path.read_text(encoding="utf-8").splitlines()
    entries: List[dict] = []
    layer = None
    in_table = False
    for line in lines:
        heading = re.match(r"##+\s+\d+(?:\.\d+)?\s+([A-Z]+)", line)
        if heading:
            layer = heading.group(1)
            in_table = False
            continue
        if line.startswith("| 错误码 |"):
            in_table = True
            continue
        if in_table and line.startswith("| ---"):
            continue
        if in_table and line.startswith("|"):
            parts = [item.strip() for item in line.strip("|").split("|")]
            if len(parts) >= 2 and parts[0] and parts[0] != "错误码":
                code = parts[0].strip("` ")
                description = parts[1].strip()
                entries.append(
                    {
                        "errorCode": code,
                        "layer": layer or "UNKNOWN",
                        "description": description,
                        "docRefs": DOC_REF_MAP.get(code, ["docs/phase-1/error-codes.md"]),
                        "fixHint": FIX_HINT_MAP.get(code, "回看对应契约文档，定位失败阶段与字段后修复。"),
                        "ownerModule": owner_module_for(code),
                        "sourcePath": first_source_path_for(code),
                        "lineHint": first_line_hint_for(code),
                    }
                )
            continue
        if in_table and line.strip() == "":
            in_table = False
    return entries + SUPPLEMENTAL_ERROR_CODES


def owner_module_for(code: str) -> str:
    if code in {
        "CYCLIC_GRAPH",
        "NO_START_NODE",
        "NO_TERMINAL_NODE",
        "UNREACHABLE_NODE",
        "DISCONNECTED_SUBGRAPH",
        "NODE_NOT_ON_START_TO_END_PATH",
        "INVALID_CONDITION_BRANCH",
        "FLOW_OUTPUT_MAPPING_MISSING",
        "NODE_SCHEMA_INVALID",
    }:
        return "fluxion-modeler"
    if code in {
        "NODE_INPUT_EVAL_FAILED",
        "NODE_OUTPUT_EVAL_FAILED",
        "NODE_TIMEOUT",
        "RESOURCE_PERMIT_EXHAUSTED",
        "HTTP_CALL_FAILED",
        "DB_QUERY_FAILED",
        "DB_UPDATE_FAILED",
    }:
        return "fluxion-engine"
    if code in {"SYNC_TIMEOUT", "FLOW_FAILED", "INSTANCE_RUNNING"}:
        return "fluxion-runtime-api"
    if code in {"REJECTED_CONCURRENCY", "WAIT_TIMEOUT", "DISPATCH_FAILED"}:
        return "fluxion-scheduler"
    return "fluxion-common"


def first_source_path_for(code: str) -> str:
    ref = DOC_REF_MAP.get(code, ["docs/phase-1/error-codes.md"])[0]
    return ref.split("#", 1)[0]


def first_line_hint_for(code: str) -> str:
    return LINE_HINT_MAP.get(code, "section-error-codes")


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    error_codes = {
        "version": "1.0",
        "generatedFrom": "docs/phase-1/error-codes.md",
        "entries": parse_error_codes(),
    }
    runtime_rules = {
        "version": "1.0",
        "generatedFrom": "docs/phase-1/runtime-semantics.md",
        "rules": RUNTIME_RULES,
    }
    write_json(GENERATED_DIR / "error-codes.json", error_codes)
    write_json(GENERATED_DIR / "runtime-rules.json", runtime_rules)
    write_json(GENERATED_DIR / "diagnostic-schema.json", DIAGNOSTIC_SCHEMA)
    print(f"generated catalogs in {GENERATED_DIR}")


if __name__ == "__main__":
    main()
