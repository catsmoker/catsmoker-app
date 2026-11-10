# Selection only: no native app module is built for Play. Keeping full sources
# outside main prevents the Unity/target-process implementation entering Play.
LOCAL_PATH := $(call my-dir)
ifeq ($(CATSMOKER_FULL),1)
include $(CLEAR_VARS)
LOCAL_MODULE := catsmoker_bridge
LOCAL_SRC_FILES := ../../full/jni/catsmoker_bridge.c ../../full/jni/catsmoker_spoof.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
endif
