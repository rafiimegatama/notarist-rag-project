import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import { useAuth } from '../contexts/AuthContext';
import { listDocuments } from '../api/documents';

function StatCard({ label, value, color }) {
  return (
    <View style={[styles.statCard, { borderLeftColor: color }]}>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

export default function HomeScreen({ navigation }) {
  const { user, signOut } = useAuth();
  const [stats, setStats] = useState({ total: 0, processing: 0, ready: 0 });
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadStats = async () => {
    try {
      const data = await listDocuments(0, 1);
      const total = data.data?.page?.totalElements ?? 0;
      setStats({
        total,
        processing: 0,
        ready: total,
      });
    } catch (_) {
      // backend may not be running — show zeros
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => { loadStats(); }, []);

  const onRefresh = () => {
    setRefreshing(true);
    loadStats();
  };

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Selamat Datang</Text>
          <Text style={styles.appName}>Notarist RAG Platform</Text>
        </View>
        <TouchableOpacity onPress={signOut} style={styles.logoutBtn}>
          <Text style={styles.logoutText}>Keluar</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.sectionTitle}>Ringkasan Dokumen</Text>
      {loading ? (
        <ActivityIndicator color="#3B82F6" style={{ marginTop: 24 }} />
      ) : (
        <View style={styles.statsRow}>
          <StatCard label="Total Dokumen" value={stats.total} color="#3B82F6" />
          <StatCard label="Diproses" value={stats.processing} color="#F59E0B" />
          <StatCard label="Siap Cari" value={stats.ready} color="#10B981" />
        </View>
      )}

      <Text style={styles.sectionTitle}>Aksi Cepat</Text>
      <View style={styles.actionsGrid}>
        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => navigation.navigate('Assistant')}
        >
          <Text style={styles.actionIcon}>🤖</Text>
          <Text style={styles.actionTitle}>Tanya AI</Text>
          <Text style={styles.actionDesc}>Tanya dokumen hukum Anda</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => navigation.navigate('Documents')}
        >
          <Text style={styles.actionIcon}>📄</Text>
          <Text style={styles.actionTitle}>Dokumen</Text>
          <Text style={styles.actionDesc}>Kelola dokumen notaris</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.infoBox}>
        <Text style={styles.infoTitle}>ℹ️ Tentang Sistem</Text>
        <Text style={styles.infoText}>
          Notarist RAG Platform adalah sistem manajemen dokumen berbasis AI untuk
          Kantor Notaris dan PPAT. Unggah akta, sertifikat, dan dokumen hukum
          lainnya untuk pencarian cerdas dengan AI.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    paddingTop: 16,
    backgroundColor: '#1E293B',
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  greeting: {
    color: '#94A3B8',
    fontSize: 13,
  },
  appName: {
    color: '#F1F5F9',
    fontSize: 18,
    fontWeight: '700',
  },
  logoutBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#475569',
  },
  logoutText: {
    color: '#94A3B8',
    fontSize: 13,
  },
  sectionTitle: {
    color: '#94A3B8',
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginHorizontal: 20,
    marginTop: 24,
    marginBottom: 12,
  },
  statsRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    gap: 8,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#1E293B',
    borderRadius: 10,
    padding: 14,
    borderLeftWidth: 3,
  },
  statValue: {
    color: '#F1F5F9',
    fontSize: 24,
    fontWeight: '700',
  },
  statLabel: {
    color: '#64748B',
    fontSize: 11,
    marginTop: 2,
  },
  actionsGrid: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    gap: 12,
  },
  actionCard: {
    flex: 1,
    backgroundColor: '#1E293B',
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#334155',
  },
  actionIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  actionTitle: {
    color: '#F1F5F9',
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 4,
  },
  actionDesc: {
    color: '#64748B',
    fontSize: 11,
    textAlign: 'center',
  },
  infoBox: {
    margin: 16,
    marginTop: 20,
    backgroundColor: '#1E3A5F',
    borderRadius: 10,
    padding: 16,
    borderWidth: 1,
    borderColor: '#2563EB',
  },
  infoTitle: {
    color: '#93C5FD',
    fontWeight: '600',
    marginBottom: 8,
  },
  infoText: {
    color: '#BFDBFE',
    fontSize: 13,
    lineHeight: 20,
  },
});
