#!/usr/bin/env bash
set -euo pipefail

# 清理 GenisDog 逆向素材中烘焙进 PNG 的暗色半透明按钮底。
# 规则只处理指定图标资源：低亮度像素视为背景并置透明，速度按钮和运动模式图不做抠图。

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="$ROOT_DIR/docs/assets/genisdog"
TARGET_DIR="$ROOT_DIR/app/src/main/res/drawable-nodpi"

if ! command -v magick >/dev/null 2>&1; then
    echo "需要安装 ImageMagick，并确保 magick 命令可用" >&2
    exit 1
fi

mkdir -p "$TARGET_DIR"

copy_asset() {
    local source_name="$1"
    local target_name="$2"
    cp "$SOURCE_DIR/$source_name" "$TARGET_DIR/$target_name"
}

clean_dark_background() {
    local source_name="$1"
    local target_name="$2"

    magick "$SOURCE_DIR/$source_name" \
        -alpha set \
        -channel A \
        -fx '((r < 0.47) && (g < 0.47) && (b < 0.47)) ? 0 : a' \
        +channel \
        -define png:color-type=6 \
        "$TARGET_DIR/$target_name"
}

clean_dark_background "battery-status-entry-icon-crop.png" "genisdog_icon_battery.png"
clean_dark_background "icon_crawl.png" "genisdog_icon_crawl.png"
clean_dark_background "icon_dog.png" "genisdog_icon_dog.png"
clean_dark_background "icon_dog_clicked.png" "genisdog_icon_dog_clicked.png"
clean_dark_background "icon_high_platform.png" "genisdog_icon_high_platform.png"
clean_dark_background "icon_lie_down.png" "genisdog_icon_lie_down.png"
clean_dark_background "icon_lock.png" "genisdog_icon_lock.png"
clean_dark_background "icon_photo.png" "genisdog_icon_photo.png"
clean_dark_background "icon_robot.png" "genisdog_icon_robot.png"
clean_dark_background "icon_setting.png" "genisdog_icon_setting.png"
clean_dark_background "icon_slim.png" "genisdog_icon_slim.png"
clean_dark_background "icon_small_spinning_top.png" "genisdog_icon_spinning.png"
clean_dark_background "icon_stand.png" "genisdog_icon_stand.png"
clean_dark_background "icon_video.png" "genisdog_icon_video.png"

copy_asset "icon_mode_common.png" "genisdog_icon_mode_common.png"
copy_asset "icon_mode_in_place.png" "genisdog_icon_mode_in_place.png"
copy_asset "icon_mode_stair.png" "genisdog_icon_mode_stair.png"
copy_asset "icon_btn_speed_high_m.png" "genisdog_speed_high.png"
copy_asset "icon_btn_speed_medium_m.png" "genisdog_speed_medium.png"
copy_asset "icon_btn_speed_slow_m.png" "genisdog_speed_slow.png"

echo "GenisDog 素材已清理并输出到 $TARGET_DIR"
