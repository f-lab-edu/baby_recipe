import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import Navbar from './components/Navbar'
import PrivateRoute from './components/PrivateRoute'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'
import RecipeDetail from './pages/RecipeDetail'
import RecipeForm from './pages/RecipeForm'
import Feed from './pages/Feed'
import UserProfile from './pages/UserProfile'
import UserSearch from './pages/UserSearch'
import EditProfile from './pages/EditProfile'
import ChatRoomList from './pages/ChatRoomList'
import ChatRoom from './pages/ChatRoom'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Navbar />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/recipes/:id" element={<RecipeDetail />} />
          <Route path="/users/search" element={<PrivateRoute><UserSearch /></PrivateRoute>} />
          <Route path="/users/:id" element={<UserProfile />} />
          <Route path="/recipes/new" element={<PrivateRoute><RecipeForm /></PrivateRoute>} />
          <Route path="/recipes/:id/edit" element={<PrivateRoute><RecipeForm /></PrivateRoute>} />
          <Route path="/feed" element={<PrivateRoute><Feed /></PrivateRoute>} />
          <Route path="/profile/edit" element={<PrivateRoute><EditProfile /></PrivateRoute>} />
          <Route path="/chat" element={<PrivateRoute><ChatRoomList /></PrivateRoute>} />
          <Route path="/chat/:roomId" element={<PrivateRoute><ChatRoom /></PrivateRoute>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
