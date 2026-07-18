import React, { useEffect, useRef, useState } from 'react';
import { View, TouchableOpacity, LayoutAnimation, Platform, UIManager } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

// A generic, data-driven expandable tree (Sprint 7). Each node:
//   { id, label, icon?, right?, children?, onPress?, defaultExpanded?, accessibilityLabel?, muted? }
// A node with children is an expandable branch (tap toggles); a leaf uses onPress. Presentation only —
// the caller builds the node array, so search/filter live where the data does, not in here.
//
// Expansion is local state seeded from defaultExpanded, but RE-SYNCS when defaultExpanded changes, so
// a parent can auto-open a branch (e.g. the document branch while a search is active) without the
// component being fully controlled. Manual toggles in between are preserved until the next such change.
function TreeNode({ node, depth }) {
  const theme = useTheme();
  const hasChildren = Array.isArray(node.children) && node.children.length > 0;
  const [expanded, setExpanded] = useState(node.defaultExpanded ?? depth === 0);
  const prevDefault = useRef(node.defaultExpanded);

  useEffect(() => {
    if (node.defaultExpanded !== prevDefault.current) {
      prevDefault.current = node.defaultExpanded;
      if (typeof node.defaultExpanded === 'boolean') setExpanded(node.defaultExpanded);
    }
  }, [node.defaultExpanded]);

  const toggle = () => {
    LayoutAnimation.configureNext(
      LayoutAnimation.create(theme.durations.fast, LayoutAnimation.Types.easeInEaseOut, LayoutAnimation.Properties.opacity),
    );
    setExpanded((e) => !e);
  };
  const onPress = hasChildren ? toggle : node.onPress;
  const interactive = hasChildren || !!node.onPress;

  return (
    <View>
      <TouchableOpacity
        onPress={onPress}
        disabled={!interactive}
        activeOpacity={0.7}
        accessibilityRole={interactive ? 'button' : 'text'}
        accessibilityState={hasChildren ? { expanded } : undefined}
        accessibilityLabel={node.accessibilityLabel || (typeof node.label === 'string' ? node.label : undefined)}
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          paddingVertical: theme.spacing.sm,
          paddingRight: theme.spacing.sm,
          paddingLeft: theme.spacing.sm + depth * theme.spacing.lg,
          minHeight: theme.touchTarget.min,
          borderBottomWidth: 1,
          borderBottomColor: theme.colors.border,
        }}
      >
        <AppText
          accessibilityElementsHidden
          importantForAccessibility="no"
          color="textFaint"
          style={{ width: 16, textAlign: 'center' }}
        >
          {hasChildren ? (expanded ? '▾' : '▸') : ''}
        </AppText>
        {node.icon ? (
          <AppText accessibilityElementsHidden importantForAccessibility="no" style={{ fontSize: 16, marginRight: theme.spacing.sm }}>
            {node.icon}
          </AppText>
        ) : null}
        <AppText
          variant="bodySm"
          color={node.muted ? 'textFaint' : 'text'}
          numberOfLines={1}
          style={{ flex: 1 }}
        >
          {node.label}
        </AppText>
        {node.right ? <View style={{ marginLeft: theme.spacing.sm }}>{node.right}</View> : null}
      </TouchableOpacity>
      {expanded && hasChildren
        ? node.children.map((child) => <TreeNode key={child.id} node={child} depth={depth + 1} />)
        : null}
    </View>
  );
}

export default function TreeView({ nodes = [], style }) {
  return (
    <View style={style}>
      {nodes.map((n) => (
        <TreeNode key={n.id} node={n} depth={0} />
      ))}
    </View>
  );
}
