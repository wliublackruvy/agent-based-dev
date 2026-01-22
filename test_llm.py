import os
from termcolor import colored
from agents.lib.llm import parse_json_response, call_llm
from agents.config import AGENT_CONFIG

# 仅测试当前支持的三种底层模型
MODEL_TESTS = [
    {
        "name": "Codex GPT",
        "provider": "codex",
        "system": "You are running a Codex CLI connectivity check.",
        "user": "Reply with 'Codex OK' and nothing else.",
        "expect": "codex ok",
        "fallback_model": "gpt-5.2"
    },
    {
        "name": "DeepSeek Chat",
        "provider": "deepseek",
        "system": "你是 DeepSeek API 连通性测试助手。",
        "user": "请只回复 'DeepSeek OK'",
        "expect": "deepseek ok",
        "fallback_model": "deepseek-chat",
        "requires_env": "DEEPSEEK_API_KEY"
    },
    {
        "name": "Qwen CLI",
        "provider": "qwen",
        "system": "You are a Qwen CLI smoke test.",
        "user": "Say exactly 'Qwen OK'",
        "expect": "qwen ok",
        "fallback_model": "qwen-coder-turbo"
    }
]


def test_llm_integration():
    print(colored("=== 🚀 开始 LLM 底座集成验证 (Multi-Model Integration Test) ===", "blue", attrs=["bold"]))

    total_agents = len(MODEL_TESTS)
    success_count = 0
    for idx, case in enumerate(MODEL_TESTS, start=1):
        if run_provider_check(case, idx, total_agents):
            success_count += 1

    print(colored(f"\n>>> 模型连通性概览: {success_count}/{total_agents} 个模型响应成功", "cyan"))

    # JSON 解析器鲁棒性测试
    print(colored("\n[JSON] 验证 JSON 解析器 (处理 Markdown 包裹)...", "yellow"))
    dirty_json = """
    好的，这是你要的任务列表：
    ```json
    {
      "status": "success",
      "data": {"task_count": 2}
    }
    ```
    希望对你有帮助。
    """
    parsed = parse_json_response(dirty_json)
    if parsed and parsed.get("status") == "success":
        print(colored("✅ JSON 解析成功 (已正确过滤 Markdown 标记)", "green"))
    else:
        print(colored("❌ JSON 解析失败", "red"))

    print(colored("\n" + "=" * 50, "blue"))
    print(colored("🏁 验证结束", "blue", attrs=["bold"]))


def run_provider_check(case: dict, idx: int, total: int) -> bool:
    provider = case["provider"]
    env_key = case.get("requires_env")
    if env_key and not os.getenv(env_key):
        print(colored(f"\n[{idx}/{total}] {case['name']} -> {provider}", "yellow"))
        print(colored(f"⚠️ 跳过: 未检测到 {env_key} 环境变量", "magenta"))
        return False

    agent_name, cfg = find_agent_for_provider(provider)
    model_id = (cfg or {}).get("model_id") or case.get("fallback_model")
    origin = f"agent '{agent_name}'" if agent_name else "fallback configuration"
    print(colored(f"\n[{idx}/{total}] 测试 {case['name']} ({provider}/{model_id}) - 来源: {origin}", "yellow"))

    response = call_llm(
        case["system"],
        case["user"],
        model_type=provider,
        model_id=model_id,
        json_mode=case.get("json_mode", False)
    )
    if not response:
        print(colored("❌ 模型无响应或调用失败", "red"))
        return False

    if case["expect"] in response.lower():
        print(colored(f"✅ 响应成功: {response}", "green"))
        return True

    print(colored("❌ 响应内容异常，未匹配期望关键字", "red"))
    print(f"   Output: {response}")
    return False


def find_agent_for_provider(provider: str):
    for name, cfg in AGENT_CONFIG.items():
        if cfg.get("provider") == provider:
            return name, cfg
    return None, None


if __name__ == "__main__":
    # 确保当前路径在 PYTHONPATH 中以便导入 agents 模块
    import sys

    current_dir = os.getcwd()
    if current_dir not in sys.path:
        sys.path.append(current_dir)

    test_llm_integration()
