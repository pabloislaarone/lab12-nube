import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Toaster } from 'sonner';
import { useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import Admin from './pages/Admin.jsx';
import JoinRoom from './pages/JoinRoom.jsx';
import Login from './pages/Login.jsx';
import Matches from './pages/Matches.jsx';
import Ranking from './pages/Ranking.jsx';
import RoomDetail from './pages/RoomDetail.jsx';
import Rooms from './pages/Rooms.jsx';
import Rules from './pages/Rules.jsx';
import Stats from './pages/Stats.jsx';

function RequireAuth({ children, admin }) {
  const { user, ready, isAdmin } = useAuth();
  const location = useLocation();
  if (!ready) return null;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  if (admin && !isAdmin) return <Navigate to="/partidos" replace />;
  return children;
}

export default function App() {
  return (
    <>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/partidos" element={<Matches />} />
          <Route path="/salas" element={<Rooms />} />
          <Route path="/salas/:id" element={<RoomDetail />} />
          <Route path="/unirse/:code" element={<JoinRoom />} />
          <Route path="/ranking" element={<Ranking />} />
          <Route path="/estadisticas" element={<Stats />} />
          <Route path="/reglas" element={<Rules />} />
          <Route
            path="/admin"
            element={
              <RequireAuth admin>
                <Admin />
              </RequireAuth>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/partidos" replace />} />
      </Routes>
      <Toaster position="bottom-right" theme="system" richColors={false} closeButton={false} offset={20} />
    </>
  );
}
