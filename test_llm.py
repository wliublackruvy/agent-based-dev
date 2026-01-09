import os
import json
from termcolor import colored
from agents.lib.llm import call_llm, parse_json_response, call_llm_for_agent
from agents.config import AGENT_CONFIG
import agents.config as agent_config_module

def test_llm_integration():
    print(colored("=== 🚀 开始 LLM 底座集成验证 (Multi-Model Integration Test) ===", "blue", attrs=["bold"]))

    # 1. 验证 Qwen CLI (模拟 Coder Agent 链路)
    print(colored("\n[1/4] 验证 Qwen CLI (Coder Agent 链路)...", "yellow"))
    # 从 config 获取预设型号进行测试
    coder_model = AGENT_CONFIG.get("coder", {}).get("model_id", "qwen-coder-turbo")
    res_qwen = call_llm_for_agent("coder", "你是一个程序员", "请输出一行 Python 代码打印 'Qwen is Ready'")
    
    if res_qwen and "print" in res_qwen.lower():
        print(colored(f"✅ Qwen ({coder_model}) 响应成功:", "green"))
        print(f"   Output: {res_qwen}")
    else:
        print(colored(f"❌ Qwen 响应异常，请检查 qwen CLI 是否安装且支持 -m {coder_model}", "red"))

    # 2. 验证 DeepSeek API (模拟 Architect Agent 链路)
    print(colored("\n[2/4] 验证 DeepSeek API (Architect Agent 链路)...", "yellow"))
    if not os.getenv("DEEPSEEK_API_KEY"):
        print(colored("⚠️ 跳过: 未检测到 DEEPSEEK_API_KEY 环境变量", "magenta"))
    else:
        ds_model = AGENT_CONFIG.get("architect", {}).get("model_id", "deepseek-chat")
        res_ds = call_llm_for_agent("architect", "你是一个架构师", "你好，请简单回复 'DeepSeek OK'")
        if res_ds and "OK" in res_ds.upper():
            print(colored(f"✅ DeepSeek ({ds_model}) 响应成功", "green"))
        else:
            print(colored(f"❌ DeepSeek 响应失败，请检查 API Key 和网络", "red"))

    # 3. 验证 Codex CLI (模拟 Reviewer Agent 链路)
    print(colored("\n[3/4] 验证 Codex CLI (Reviewer Agent 链路)...", "yellow"))
    # 模拟你提到的 gpt-4o 等具体型号设置
    reviewer_model = AGENT_CONFIG.get("reviewer", {}).get("model_id", "gpt-4o")
    config_path = getattr(agent_config_module, "__file__", "agents/config.py")
    print(colored(f"   当前 reviewer 模型: {reviewer_model} (配置文件: {config_path})", "cyan"))
    res_codex = call_llm_for_agent("reviewer", "You are a reviewer", "Say 'Codex OK'")
    
    if res_codex and "OK" in res_codex.upper():
        print(colored(f"✅ Codex ({reviewer_model}) 响应成功:", "green"))
        print(f"   Output: {res_codex}")
    elif "not supported" in str(res_codex):
        print(colored(f"❌ Codex 报错: 模型 {reviewer_model} 不受支持，请在 config.py 中更换型号", "red"))
    else:
        print(colored("❌ Codex 响应失败，请检查 'codex' 命令和登录状态", "red"))

    # 4. 验证 JSON 解析器 (鲁棒性测试)
    print(colored("\n[4/4] 验证 JSON 解析器 (处理 Markdown 包裹)...", "yellow"))
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

    print(colored("\n" + "="*50, "blue"))
    print(colored("🏁 验证结束", "blue", attrs=["bold"]))

if __name__ == "__main__":
    # 确保当前路径在 PYTHONPATH 中以便导入 agents 模块
    import sys
    current_dir = os.getcwd()
    if current_dir not in sys.path:
        sys.path.append(current_dir)
        
    test_llm_integration()
