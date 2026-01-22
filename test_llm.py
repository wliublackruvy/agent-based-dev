import os
import sys
from termcolor import colored

# Ensure agents module can be imported
current_dir = os.getcwd()
if current_dir not in sys.path:
    sys.path.append(current_dir)

from agents.lib.llm import parse_json_response, call_llm
from agents.config import AGENT_CONFIG

# Define comprehensive test cases
MODEL_TEST_CASES = [
    # --- Codex Provider Tests ---
    {
        "name": "Codex GLM Profile",
        "provider": "codex",
        "model_id": "glm",
        "system": "You are a GLM connectivity test.",
        "user": "Reply with 'GLM OK' only.",
        "expect_keyword": "ok" 
    },
    {
        "name": "Codex GPT-5",
        "provider": "codex",
        "model_id": "gpt-5",
        "system": "You are a GPT-5 connectivity test.",
        "user": "Reply with 'GPT-5 OK' only.",
        "expect_keyword": "ok"
    },
    
    # --- DeepSeek Provider Tests ---
    {
        "name": "DeepSeek Chat",
        "provider": "deepseek",
        "model_id": "deepseek-chat",
        "system": "You are a DeepSeek connectivity test.",
        "user": "Reply with 'DeepSeek OK' only.",
        "expect_keyword": "ok",
        "requires_env": "DEEPSEEK_API_KEY"
    },
    
    # --- Qwen Provider Tests ---
    {
        "name": "Qwen Coder Turbo",
        "provider": "qwen",
        "model_id": "qwen-coder-turbo",
        "system": "You are a Qwen connectivity test.",
        "user": "Reply with 'Qwen OK' only.",
        "expect_keyword": "ok"
    }
]

def test_llm_integration():
    print(colored("=== 🚀 LLM Integration & Model Routing Test ===", "blue", attrs=["bold"]))
    print(colored("Checking multiple providers and specific model IDs...\n", "cyan"))

    total_tests = len(MODEL_TEST_CASES)
    passed_count = 0

    for i, case in enumerate(MODEL_TEST_CASES, 1):
        if run_single_test(case, i, total_tests):
            passed_count += 1

    print(colored(f"\n>>> Test Summary: {passed_count}/{total_tests} passed.", "cyan" if passed_count == total_tests else "yellow"))

    # Test JSON Parser
    test_json_parser()

    print(colored("\n" + "=" * 50, "blue"))
    print(colored("🏁 Test Suite Completed", "blue", attrs=["bold"]))

def run_single_test(case: dict, index: int, total: int) -> bool:
    name = case["name"]
    provider = case["provider"]
    model_id = case.get("model_id")
    
    print(colored(f"[{index}/{total}] Testing: {name} (Provider: {provider}, Model: {model_id})", "yellow"))

    # Check Env Deps
    env_key = case.get("requires_env")
    if env_key and not os.getenv(env_key):
        print(colored(f"⚠️  Skipping: Missing environment variable {env_key}", "magenta"))
        return False

    # Execute
    try:
        response = call_llm(
            system_prompt=case["system"],
            user_prompt=case["user"],
            model_type=provider,
            model_id=model_id,
            json_mode=False
        )
        
        if not response:
            print(colored("❌ Failed: No response received.", "red"))
            return False
            
        print(colored(f"   Response: {response.strip()}", "white"))
        
        # Validation
        if case["expect_keyword"].lower() in response.lower():
            print(colored("✅ Passed", "green"))
            return True
        else:
            print(colored(f"❌ Failed: Expected keyword '{case['expect_keyword']}' not found.", "red"))
            return False

    except Exception as e:
        print(colored(f"❌ Exception: {str(e)}", "red"))
        return False

def test_json_parser():
    print(colored("\n[JSON] Testing JSON Parser Robustness...", "yellow"))
    dirty_json = """
    Here is the data:
    ```json
    {
      "status": "success",
      "data": [1, 2, 3]
    }
    ```
    """
    parsed = parse_json_response(dirty_json)
    if parsed and parsed.get("status") == "success":
        print(colored("✅ JSON Parser: Correctly extracted JSON from Markdown.", "green"))
    else:
        print(colored("❌ JSON Parser: Failed to extract JSON.", "red"))

if __name__ == "__main__":
    test_llm_integration()