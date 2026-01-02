import subprocess
import json
import os
from termcolor import colored

# 这里配置你在 agent.sh 里使用的模型名称
CODEX_MODEL = os.getenv("CODEX_MODEL", "gpt-5-codex") 

def call_llm(system_prompt: str, user_prompt: str, json_mode: bool = False) -> str:
    """
    通过 subprocess 调用本地的 'codex' 命令行工具。
    """
    
    # 组合完整的 Prompt
    full_prompt = f"{system_prompt}\n\n{user_prompt}"
    if json_mode:
        full_prompt += "\n\nIMPORTANT: Output valid JSON only. Do not wrap in markdown blocks."

    print(colored(f"🤖 Calling Codex CLI ({CODEX_MODEL})...", "cyan"))

    try:
        # 构造命令： echo "prompt" | codex exec -m model -
        # 注意：这里模拟了你 agent.sh 里的管道用法
        process = subprocess.Popen(
            ["codex", "exec", "-m", CODEX_MODEL, "-"], 
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        # 发送 prompt 到 stdin，并获取输出
        stdout, stderr = process.communicate(input=full_prompt)

        if process.returncode != 0:
            print(colored(f"❌ Codex CLI Error: {stderr}", "red"))
            return ""

        return stdout.strip()

    except FileNotFoundError:
        print(colored("❌ Error: 'codex' command not found. Make sure it is in your PATH.", "red"))
        return ""
    except Exception as e:
        print(colored(f"❌ Unexpected Error: {e}", "red"))
        return ""

def parse_json_response(response_text: str):
    """
    解析 JSON，处理 Codex 可能返回的 ```json 包裹
    """
    # 去掉 markdown 代码块标记
    clean_text = response_text.replace("```json", "").replace("```", "").strip()
    try:
        return json.loads(clean_text)
    except json.JSONDecodeError:
        print(colored(f"❌ JSON Parse Error. Raw output:\n{response_text}", "red"))
        return None