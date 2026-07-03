import React from 'react';
import { ActivityIndicator, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Text } from 'react-native';

import { useAuth } from '../contexts/AuthContext';
import LoginScreen from '../screens/LoginScreen';
import HomeScreen from '../screens/HomeScreen';
import DocumentsScreen from '../screens/DocumentsScreen';
import AssistantScreen from '../screens/AssistantScreen';

const Stack = createNativeStackNavigator();
const Tab = createBottomTabNavigator();

function TabIcon({ name, color }) {
  const icons = { Beranda: '🏠', Dokumen: '📄', Asisten: '🤖' };
  return <Text style={{ fontSize: 20 }}>{icons[name]}</Text>;
}

function MainTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ color }) => <TabIcon name={route.name} color={color} />,
        tabBarStyle: {
          backgroundColor: '#1E293B',
          borderTopColor: '#334155',
        },
        tabBarActiveTintColor: '#3B82F6',
        tabBarInactiveTintColor: '#64748B',
        headerStyle: { backgroundColor: '#1E293B' },
        headerTintColor: '#F1F5F9',
        headerShadowVisible: false,
      })}
    >
      <Tab.Screen name="Beranda" component={HomeScreen} options={{ title: 'Beranda' }} />
      <Tab.Screen name="Dokumen" component={DocumentsScreen} options={{ title: 'Dokumen' }} />
      <Tab.Screen name="Asisten" component={AssistantScreen} options={{ title: 'Asisten AI' }} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0F172A' }}>
        <ActivityIndicator color="#3B82F6" size="large" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {user ? (
          <Stack.Screen name="Main" component={MainTabs} />
        ) : (
          <Stack.Screen name="Login" component={LoginScreen} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
