package com.leo.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.leo.common.Config;
import com.leo.common.UI;
import com.leo.util.PhoneController;

import java.util.List;

/**
 * Created by leo on 2016/8/4.
 * 自动回复服务
 */
public class AutoReplyService extends AccessibilityService{

    private static final String TAG = AutoReplyService.class.getSimpleName();

    private Handler handler = new Handler();
    private boolean hasNotify = false;

    /**
     * 必须重写的方法，响应各种事件。
     */
    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        int eventType = event.getEventType(); // 事件类型
        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: // 通知栏事件
                Log.i(TAG, "TYPE_NOTIFICATION_STATE_CHANGED");
                if(PhoneController.isLockScreen(this)) { // 锁屏
                    PhoneController.wakeAndUnlockScreen(this);   // 唤醒点亮屏幕
                }
                openAppByNotification(event);
                hasNotify = true;
                break;

            default:
                Log.i(TAG, "DEFAULT");
                if (hasNotify) {
                    try {
                        Thread.sleep(1000); // 停1秒, 否则在微信主界面没进入聊天界面就执行了fillInputBar
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (forwardGo("独角兽们都在讲故事了", "机器人和机器人的对话")) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);   // 返回
                            }
                        }, 1500);
                    }
                    /*if (fillInputBar("我在敲代码，稍后回复哈~")) {
                        findAndPerformAction(UI.BUTTON, "发送");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);   // 返回
                            }
                        }, 1500);

                    }*/
                    hasNotify = false;
                }
                break;
        }
    }


    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt");
    }


    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.packageNames = new String[]{Config.WX_PACKAGE_NAME};
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.notificationTimeout = 100;
        this.setServiceInfo(info);
    }

    /**
     * 查找UI控件并点击
     * @param widget 控件完整名称, 如android.widget.Button, android.widget.TextView
     * @param text 控件文本
     */
    private void findAndPerformAction(String widget, String text) {
        // 取得当前激活窗体的根节点
        if (getRootInActiveWindow() == null) {
            return;
        }

        // 通过文本找到当前的节点
        List<AccessibilityNodeInfo> nodes = getRootInActiveWindow().findAccessibilityNodeInfosByText(text);
        if(nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.getClassName().equals(widget) && node.isEnabled()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK); // 执行点击
                    break;
                }
            }
        }
    }



    /**
     * 打开微信
     * @param event 事件
     */
    private void openAppByNotification(AccessibilityEvent event) {
        if (event.getParcelableData() != null  && event.getParcelableData() instanceof Notification) {
            Notification notification = (Notification) event.getParcelableData();
            try {
                PendingIntent pendingIntent = notification.contentIntent;
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
    }



    /**
     * 填充输入框
     */
    private boolean fillInputBar(String reply) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            return findInputBar(rootNode, reply);
        }
        return false;
    }



    /**
     * 查找EditText控件
     * @param rootNode 根结点
     * @param reply 回复内容
     * @return 找到返回true, 否则返回false
     */
    private boolean findInputBar(AccessibilityNodeInfo rootNode, String reply) {
        int count = rootNode.getChildCount();
        Log.i(TAG, "root class=" + rootNode.getClassName() + ", " + rootNode.getText() + ", child: " + count);
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo node = rootNode.getChild(i);
            if (UI.EDITTEXT.equals(node.getClassName())) {   // 找到输入框并输入文本
                Log.i(TAG, "****found the EditText");
                setText(node, reply);
                return true;
            }

            if (findInputBar(node, reply)) {    // 递归查找
                return true;
            }
        }
        return false;
    }


    /**
     * 设置文本
     */
    private void setText(AccessibilityNodeInfo node, String reply) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.i(TAG, "set text");
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    reply);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } else {
            ClipData data = ClipData.newPlainText("reply", reply);
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(data);
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS); // 获取焦点
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE); // 执行粘贴
        }
    }

    private AccessibilityNodeInfo getTheSharingNode(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        List<AccessibilityNodeInfo> nodes;
        AccessibilityNodeInfo lastNode;

        nodes = rootNode.findAccessibilityNodeInfosByText(text);

        if (nodes != null && !nodes.isEmpty()) {
            lastNode = nodes.get(nodes.size() - 1);
            return getLongClickableParent(lastNode);
        }

        return null;

    }

    private AccessibilityNodeInfo getLongClickableParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        AccessibilityNodeInfo tmpNode = n;

        while(!tmpNode.equals(rootNode)) {
            Log.i(TAG, "--------debug: "+tmpNode.getText());
            Log.i(TAG, tmpNode.toString());
            Log.i(TAG, tmpNode.getClassName().toString());
            Log.i(TAG, String.valueOf(tmpNode.isLongClickable()));
            Log.i(TAG, "--------debug: finished");
            if (tmpNode.isLongClickable()) {
                return tmpNode;
            }
            tmpNode = tmpNode.getParent();
        }

        return null;

    }

    private AccessibilityNodeInfo getClickableNodeByText(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        List<AccessibilityNodeInfo> nodes;
        AccessibilityNodeInfo tmpNode;

        nodes = rootNode.findAccessibilityNodeInfosByText(text);

        if (nodes != null && !nodes.isEmpty()) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                tmpNode = nodes.get(i);
                if (!UI.EDITTEXT.equals(tmpNode.getClassName())) {
                    return getClickableParent(tmpNode);
                }
            }
        }

        return null;

    }

    private AccessibilityNodeInfo getClickableParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        AccessibilityNodeInfo tmpNode = n;

        while(!tmpNode.equals(rootNode)) {
            Log.i(TAG, "--------debug: "+tmpNode.getText());
            Log.i(TAG, tmpNode.toString());
            Log.i(TAG, tmpNode.getClassName().toString());
            Log.i(TAG, String.valueOf(tmpNode.isClickable()));
            Log.i(TAG, "--------debug: finished");
            if (tmpNode.isClickable()) {
                return tmpNode;
            }
            tmpNode = tmpNode.getParent();
        }

        return null;

    }

    private boolean forwardGo(String forwardTitle, String forwardTarget) {
        AccessibilityNodeInfo toShare = getTheSharingNode(forwardTitle), toShareTo, sendShare;
        Log.i(TAG, "toshare:"+toShare.toString());
        if (toShare != null) {
            toShare.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            AccessibilityNodeInfo forward = getClickableNodeByText("转发");
            Log.i(TAG, "forward:" + forward.toString());
            if (forward != null) {
                forward.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (fillInputBar("机器人和机器人的对话")) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    toShareTo = getClickableNodeByText(forwardTarget);
                    Log.i(TAG, "toShareTo:" + toShareTo.toString());
                    if (toShareTo != null) {
                        toShareTo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        sendShare = getClickableNodeByText("发送");
                        Log.i(TAG, "sendShare:" + sendShare.toString());
                        sendShare.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        }
        return false;
    }


}
