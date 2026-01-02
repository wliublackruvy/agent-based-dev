import sys
import os

# === 关键修正开始 ===
# 获取当前脚本所在路径
current_dir = os.path.dirname(os.path.abspath(__file__))

# 手动将 .agents 文件夹加入到 Python 的搜索路径中
# 这样我们可以跳过 ".agents" 这个名字，直接 import 里面的内容
sys.path.append(os.path.join(current_dir, ".agents"))

# 现在直接从 lib.llm 导入 (注意：前面不需要 .agents 了)
try:
    from lib.llm import call_llm, parse_json_response
except ImportError as e:
    print(f"❌ Import Error: {e}")
    print("提示：请检查 .agents/lib/llm.py 文件是否存在。")
    sys.exit(1)
# === 关键修正结束 ===

print("=== Testing Codex Bridge ===")

# 1. 简单测试
print("1. Sending text request...")
reply = call_llm("You are a helpful assistant.", "Say 'Codex is Online' and nothing else.")
print(f"Reply: {reply}")

# 2. JSON 测试 (这是 PM Agent 能否工作的关键)
print("\n2. Sending JSON request...")
json_reply = call_llm(
    "You are a system tool.", 
    "Return this exact JSON: {\"status\": \"connected\", \"user\": \"admin\"}",
    json_mode=True
)
data = parse_json_response(json_reply)
print(f"Parsed Data: {data}")

if data and data.get("status") == "connected":
    print("\n✅ SUCCESS: Python is successfully controlling Codex!")
else:
    print("\n❌ FAILURE: Something went wrong.")