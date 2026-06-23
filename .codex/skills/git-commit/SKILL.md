---
name: git-commit
description: 按照 Conventional Commits 规范生成中文提交信息，或执行 git commit 操作。
---

# Git Commit

## 触发条件

当用户要求以下任一操作时：

- “写提交信息”
- “生成 commit message”
- “commit 一下”
- “提交代码”
- 或 AI 主动需要执行 `git commit` 时

## 规范约束

提交信息必须同时满足：

1. **Conventional Commits 格式**
2. **中文描述**
3. **简明扼要**

### 格式模板

```text
<type>(<scope>): <中文描述>

<body>

<footer>
```

| 字段 | 说明 | 限制 |
| --- | --- | --- |
| `type` | 提交类型 | 必填，见下表 |
| `scope` | 影响范围 | 可选，如 `skills`、`scripts`、`docs`、`qt-build` |
| `subject` | 简短描述 | 必填，中文，不超过 50 字，句末不加句号 |
| `body` | 详细说明 | 可选，每点以 `- ` 开头，说明做了什么/为什么做 |
| `footer` | 脚注 | 可选，如 `Closes #123`、`BREAKING CHANGE: xxx` |

### Type 对照表

| Type | 使用场景 | 示例 |
| --- | --- | --- |
| `feat` | 新功能 | `feat(skills): 添加 ROS 日志分析 Skill` |
| `fix` | 修复问题 | `fix(scripts): 修复 Windows 上 junction 创建失败` |
| `docs` | 文档变更 | `docs(readme): 更新快速开始指南` |
| `style` | 代码格式（不影响逻辑） | `style(skills): 统一 SKILL.md 缩进` |
| `refactor` | 重构（非 feat/fix） | `refactor(build): 重构项目类型检测逻辑` |
| `test` | 测试相关 | `test(validate): 增加错误模式库字段校验` |
| `chore` | 构建/工具/杂项 | `chore(deps): 更新 .gitignore 忽略规则` |

### 禁止行为

- 不使用 `update`、`fix`、`修改`、`提交代码` 等无意义描述。
- 不使用英文提交信息，专有名词除外。
- `subject` 不超过 50 字。
- 不把多个不相关的变更放在一个提交中。

## 执行步骤

### 生成 commit message

1. 分析当前 `git diff --staged` 或 `git status` 的变更内容。
2. 确定主要 type，由变更最多或最重要的内容决定。
3. 确定 scope，通常使用影响的模块或目录。
4. 用一句话中文概括 subject。
5. 如有必要，补充 body 说明细节。
6. 输出完整 commit message，询问用户是否确认。

### 执行 git commit

用户确认后：

1. 先格式化已暂存的自研 C/C++ 代码并重新暂存：

   ```bash
   scripts/format_owned_code.sh --staged --restage
   ```

2. 再执行提交：

```bash
git commit -m "type(scope): 中文描述

- 变更点1
- 变更点2"
```

## 示例

### 示例 1：添加新功能

```text
feat(skills): 添加 Qt 编译错误自动修复脚本

- 检测 moc 缓存导致的 undefined reference 错误
- 自动执行 qmake 重建并清理 build 目录
- 更新 error_patterns.json 增加 vtable 匹配规则
```

### 示例 2：修复问题

```text
fix(scripts): 修复 Windows 上创建 symlink 失败

- 原实现直接使用 os.symlink，在 Windows 需管理员权限
- 改为先尝试 mklink /J 创建 junction，失败则回退到目录复制
- 增加用户提示，说明手动处理方式
```

### 示例 3：文档更新

```text
docs(impl): 补充 Git Submodule 移除流程说明

- 添加 git submodule deinit 步骤
- 补充 .git/modules 清理说明
- 增加常见错误的排查指引
```

### 示例 4：简单变更，无 body

```text
style(skills): 统一所有 SKILL.md 代码块语言标记
```

## 特殊情况处理

### 多个不相关变更未分开提交

如果用户要求提交，但 staged 的变更涉及多个 scope/type：

1. 优先建议拆分提交：

   ```bash
   git add <文件1>
   git commit -m "feat(skills): 添加 xxx"
   git add <文件2>
   git commit -m "fix(scripts): 修复 yyy"
   ```

2. 用户坚持不拆分：用主要变更的 type，scope 可写多个或用 `*`：

   ```text
   feat(skills,scripts): 添加 xxx 并修复 yyy
   ```

### 变更很少

1 到 2 行变更允许无 body 的简洁提交：

```text
fix(tests): 修正 cross-compile Skill 中的示例路径
```

## 参考

- `AGENTS.md` 的“Git 提交规范”章节
- `.agents/skills/format-owned-code/SKILL.md`
- [Conventional Commits 规范](https://www.conventionalcommits.org/)