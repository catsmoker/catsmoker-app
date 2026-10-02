# In-app native bridge (see system/shell/NativeBridge.kt).
# ndk-build (not CMake): the NDK ships its own build scripts, so the spike needs no
# extra SDK component download.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := catsmoker_bridge
LOCAL_SRC_FILES := catsmoker_bridge.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
